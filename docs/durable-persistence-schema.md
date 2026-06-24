# Durable Persistence Schema

This document describes the durable persistence schema created by `V1__durable_persistence.sql` and incremental schema migrations such as `V3__session_message_content_blocks.sql`, `V7__personal_memory_rag_metadata.sql`, `V9__personal_tooling_hitl.sql`, `V11__owner_scope_persistence.sql`, and `V13__personal_authorization_policy.sql`. Vendor-specific comment migrations document the schema with V2 baseline comments and later V4/V8/V10/V12/V14 column comments.

## Personal Edition Interpretation

HarnessAgent now targets the personal edition. The canonical durable scope is owner based: `owner_scope_id`, `owner_id`, `agent_id`, `session_id`, `workspace_id` or resource-specific ids. Legacy `tenant_id` and `user_id` columns are retained only for migration metadata, old service rollback, and archive compatibility; personal code paths should read and write owner columns.

AgentScope-facing runtime identity is derived as `owner:<ownerId>`, and durable Agent state keys now use:

```text
owner:<ownerId>:agent:<agentId>:session:<sessionId>:scope:<scope>
```

The local JSON, Redis, JDBC, and AgentScope adapter stores read old keys once from:

```text
tenant:<legacyScope>:user:<ownerId>:agent:<agentId>:session:<sessionId>:scope:<scope>
```

After a successful read, stores write the owner key and delete the legacy key. If both keys exist, the owner-keyed value wins.

Earlier governed-platform authorization, reporting, and staged rollout concepts are legacy compatibility only. New personal features should treat these tables as owner/agent/session/workspace scoped records unless a future migration explicitly introduces new column names.

Flyway locations:

- Common DDL: `classpath:db/migration`
- Vendor comments: `classpath:db/vendor-migration/{vendor}`
- H2 comments: `src/main/resources/db/vendor-migration/h2/V2__durable_persistence_comments.sql`, `src/main/resources/db/vendor-migration/h2/V4__session_message_content_blocks_comments.sql`, `src/main/resources/db/vendor-migration/h2/V12__owner_scope_persistence_comments.sql`, `src/main/resources/db/vendor-migration/h2/V14__personal_authorization_policy_comments.sql`
- MySQL comments: `src/main/resources/db/vendor-migration/mysql/V2__durable_persistence_comments.sql`, `src/main/resources/db/vendor-migration/mysql/V4__session_message_content_blocks_comments.sql`, `src/main/resources/db/vendor-migration/mysql/V12__owner_scope_persistence_comments.sql`, `src/main/resources/db/vendor-migration/mysql/V14__personal_authorization_policy_comments.sql`
- Personal Memory/RAG comments: `src/main/resources/db/vendor-migration/{h2,mysql}/V8__personal_memory_rag_metadata_comments.sql`
- Personal Tooling/HITL comments: `src/main/resources/db/vendor-migration/{h2,mysql}/V10__personal_tooling_hitl_comments.sql`

The V2, V4, V6, V8, V10, V12, and V14 scripts add metadata only. They must not store prompt text, retrieval evidence, tool payloads, DSNs, Redis URIs, snapshot payloads, or workspace file data in comments.

## Table Dictionary

| Table | Purpose | Primary Access Dimensions |
|---|---|---|
| `ha_session_messages` | Session message history. | `owner_scope_id`, `owner_id`, `agent_id`, `session_id`, `created_at` |
| `ha_security_activity` | Security and authorization audit events. | `owner_scope_id`, `owner_id`, `resource_type`, `resource_id`, `occurred_at` |
| `ha_budget_counters` | Request and token budget counters. | `owner_scope_id`, `owner_id`, `agent_id`, `resource_id`, `updated_at` |
| `ha_agent_state` | Durable AgentScope state. | `owner_scope_id`, `owner_id`, `agent_id`, `session_id`, `scope`, `updated_at` |
| `ha_snapshot_metadata` | Workspace snapshot metadata. | `owner_scope_id`, `owner_id`, `agent_id`, `session_id`, `task_id`, `created_at` |
| `ha_snapshot_content` | Snapshot artifact payload linked to metadata. | `snapshot_id` |
| `ha_knowledge_sources` | RAG knowledge source metadata and access policy. | `owner_scope_id`, `owner_id`, `agent_id`, `updated_at` |
| `ha_knowledge_chunks` | Searchable chunks derived from governed sources. | `owner_scope_id`, `owner_id`, `agent_id`, `source_id`, `chunk_index` |
| `ha_personal_memories` | Explicit personal memory write records and RAG projection linkage. | `owner_scope_id`, `owner_id`, `agent_id`, `updated_at` |
| `ha_rag_metrics` | RAG hit, permission filtering, and no-answer metrics. | `owner_scope_id`, `owner_id`, `created_at` |
| `ha_rag_feedback` | User feedback on RAG answers. | `owner_scope_id`, `owner_id`, `created_at` |
| `ha_tool_definitions` | Governed tool registry. | `owner_scope_id`, `owner_id`, `name` |
| `ha_tool_activity_records` | Tool execution audit records. | `owner_scope_id`, `owner_id`, `tool_id`, `occurred_at` |
| `ha_tool_idempotency_records` | Idempotency records for mutating tool execution. | `owner_scope_id`, `owner_id`, `agent_id`, `session_id`, `tool_id` |
| `ha_tool_pending_confirmations` | Durable human-in-the-loop pause points for personal tool calls. | `owner_scope_id`, `owner_id`, `agent_id`, `session_id`, `status`, `created_at` |
| `ha_telemetry_events` | Runtime telemetry events. | `owner_scope_id`, `owner_id`, `agent_id`, `occurred_at` |
| `ha_owner_scope_migration_activity` | One-time schema migration audit metadata. | `id`, `migration_name`, `migrated_at` |

Personal edition mapping:

| Stored dimension | Personal meaning |
|---|---|
| `owner_scope_id` | Canonical personal scope, normally the literal `personal` |
| `owner_id` | Personal owner id |
| `agent_id` | Personal Agent id |
| `session_id` | Personal conversation or task session id |
| `tenant_id` | Legacy compatibility scope copied into `owner_scope_id` by V11 |
| `user_id` | Legacy user column copied into `owner_id` by V11 |
| `allowed_owners_json` | Personal authorization list for knowledge sources |
| role/department/user ACL JSON fields | Legacy compatibility for old ACLs; personal flows must use owner visibility and `allowed_owners_json` |

## Owner Migration, Backup, And Recovery

`V11__owner_scope_persistence.sql` adds owner-scope columns and backfills them from legacy columns while preserving rollback-compatible legacy columns. `V13__personal_authorization_policy.sql` adds `allowed_owners_json` and backfills it from the old user allow list. `ha_owner_scope_migration_activity` records the schema migration milestones.

Before applying owner-scope migrations, back up:

- the SQL schema and all `ha_*` durable tables;
- the local Agent state JSON directory configured by `harness-agent.production.state-store.local-directory`;
- Redis snapshots or dumps if Redis backs AgentScope state or budget counters;
- workspace, snapshot, and tool confirmation storage.

Rollback is backup-first. Once owner-scope migrations or owner-key writes have occurred, do not run an old service against the migrated schema unless the database and state stores have been restored to the pre-migration backup. There is no destructive reverse migration that drops audit, memory, tool confirmation, snapshot, or Agent state. If a manual reverse migration is required, stop all writers first and explicitly copy owner columns and owner keys back to the old layout in a staging clone before production use.

## Field Dictionary

| Table | Fields |
|---|---|
| `ha_session_messages` | `id`: message id; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `user_id`: legacy user copied into owner; `agent_id`: personal Agent id; `session_id`: conversation id; `role`: message role; `content`: legacy persisted text projection; `content_blocks_json`: structured ContentBlock JSON for text, file, media, model thinking, and tool result content; `created_at`: creation timestamp. |
| `ha_security_activity` | `id`: audit id; `occurred_at`: event timestamp; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `user_id`: legacy user copied into owner; `resource_type`: governed resource category; `resource_id`: governed resource id; `action`: audited action; `details_json`: structured audit details JSON text. |
| `ha_budget_counters` | `counter_key`: stable counter key; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `user_id`: legacy user copied into owner; `agent_id`: personal Agent id; `resource_id`: budgeted provider or resource; `requests`: consumed requests; `tokens`: consumed token estimate; `updated_at`: last update timestamp. |
| `ha_agent_state` | `state_key`: stable owner state key in `owner:<ownerId>:agent:<agentId>:session:<sessionId>:scope:<scope>` shape; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope; `user_id`: legacy compatibility user; `agent_id`: personal Agent id; `session_id`: session id; `scope`: AgentScope memory or state scope; `state_value`: serialized state text; `updated_at`: last update timestamp. |
| `ha_snapshot_metadata` | `id`: snapshot id; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `agent_id`: personal Agent id; `session_id`: session id; `task_id`: task or run id; `created_at`: snapshot timestamp; `backend_type`: storage backend type; `location`: opaque storage reference. |
| `ha_snapshot_content` | `snapshot_id`: snapshot metadata id and primary key; `content`: snapshot artifact payload. |
| `ha_knowledge_sources` | `id`: knowledge source id; `owner_scope_id`: canonical personal scope; `tenant_id`: legacy compatibility scope; `owner_id`: owner id; `agent_id`: personal Agent id when the source is Agent-scoped; `title`: display title; `version`: version label; `visibility`: visibility policy; `allowed_owners_json`: personal owner authorization JSON text; `allowed_departments_json`, `allowed_roles_json`, `allowed_users_json`: legacy ACL JSON retained for migration/archive compatibility; `update_policy`: update policy; `source_type`: source kind such as inline text, local file, local directory, URL, or memory; `source_uri`: workspace path, URL, or memory URI used for citations and export; `index_status`: searchable projection lifecycle; `indexed_at`: last successful indexing timestamp; `status`: lifecycle status; `created_at`: creation timestamp; `updated_at`: update timestamp. |
| `ha_knowledge_chunks` | `id`: chunk id; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `agent_id`: personal Agent id; `source_id`: source id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `title`: source title copied for citation; `version`: source version copied for citation; `chunk_index`: order within source; `content`: chunk text used after permission filtering; `tokens_json`: search tokens JSON text; `source_type`: source kind copied for citation reconstruction; `source_uri`: source URI copied for citation reconstruction. |
| `ha_personal_memories` | `id`: memory id; `owner_scope_id`: canonical personal scope; `tenant_id`: legacy compatibility scope retained for rollback/archive; `owner_id`: personal owner id; `agent_id`: personal Agent id; `session_id`: session where the write was requested; `layer_name`: memory layer; `title`: write summary; `content`: memory content projected to RAG only after confirmation; `status`: write lifecycle status; `source_id`: linked knowledge source id when confirmed; `created_at`: creation timestamp; `updated_at`: update timestamp. |
| `ha_rag_metrics` | `id`: metric id; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `user_id`: legacy user copied into owner; `query_text`: original retrieval query for governed analytics; `hit`: whether accessible evidence was found; `candidate_count`: candidate chunks before permission filtering; `permitted_count`: chunks after permission filtering; `failure_reason`: no-answer or retrieval failure code; `created_at`: metric timestamp. |
| `ha_rag_feedback` | `id`: feedback id; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `user_id`: legacy user copied into owner; `query_text`: retrieval query associated with feedback; `helpful`: helpful marker; `comment_text`: optional feedback comment; `created_at`: feedback timestamp. |
| `ha_tool_definitions` | `id`: tool id; `owner_scope_id`: canonical personal scope; `tenant_id`: legacy compatibility scope retained for rollback/archive; `name`: tool name; `description`: purpose description; `owner_system`: owning system; `owner_id`: personal owner id or local service id; `source_type`: source type; `source_ref`: opaque source reference; `risk_level`: governance risk; `mutating`: whether execution can mutate state; `enabled`: execution enabled flag; `parameter_schema_json`: parameter schema JSON text; `output_schema_json`: output type/schema metadata for structured results; `permission_policy_json`: personal owner/Agent permission policy JSON text; `activity_policy_json`: legacy column name for minimum record redaction policy; `workload_type`: execution workload type for sandbox routing; `created_at`: registration timestamp; `updated_at`: update timestamp. |
| `ha_tool_activity_records` | `id`: audit id; `occurred_at`: execution timestamp; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `user_id`: legacy user copied into owner; `agent_id`: personal Agent id; `session_id`: session id; `tool_id`: tool id; `tool_name`: tool name at execution time; `source_type`: source type at execution time; `risk_level`: risk level at execution time; `status`: execution status; `sanitized_input_json`: sanitized input JSON text; `sanitized_output_json`: sanitized output JSON text; `duration_millis`: duration in milliseconds; `approval_id`: legacy approval marker, no longer used by personal path; `reviewer_id`: legacy reviewer marker, no longer used by personal path; `idempotency_key`: caller idempotency key; `failure_reason`: denial or failure reason. |
| `ha_tool_idempotency_records` | `idempotency_key`: stable idempotency key; `parameter_fingerprint`: canonical parameter fingerprint; `result_json`: stored execution result JSON text; `created_at`: record creation timestamp. |
| `ha_tool_pending_confirmations` | `confirmation_id`: HITL pause id; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `user_id`: legacy user copied into owner; `agent_id`: personal Agent id; `session_id`: session id; `tool_id`: tool id; `tool_name`: tool name captured for prompt rendering; `source_type`: tool source type; `risk_level`: tool risk level; `status`: confirmation lifecycle status; `parameters_json`: original parameters for confirm resume; `sanitized_input_json`: redacted display parameters; `operation_summary_json`: user-visible summary including confirmation id; `parameter_fingerprint`: canonical parameter fingerprint; `idempotency_key`: caller idempotency key; `created_at`: creation timestamp; `updated_at`: update timestamp; `expires_at`: stale-after timestamp; `decided_at`: confirm/reject timestamp; `decision_reason`: decision note. |
| `ha_telemetry_events` | `id`: telemetry event id; `occurred_at`: event timestamp; `type`: event type; `owner_scope_id`: canonical personal scope; `owner_id`: personal owner id; `tenant_id`: legacy compatibility scope retained for rollback/archive; `user_id`: legacy user copied into owner; `agent_id`: personal Agent id; `component`: emitting component; `duration_millis`: observed duration; `attributes_json`: event attributes JSON text. |

## MySQL Metadata Verification

Run these checks after applying migrations in a MySQL schema:

```sql
select count(*) as commented_tables
from information_schema.tables
where table_schema = database()
  and table_name in (
    'ha_session_messages',
    'ha_security_activity',
    'ha_budget_counters',
    'ha_agent_state',
    'ha_snapshot_metadata',
    'ha_snapshot_content',
    'ha_knowledge_sources',
    'ha_knowledge_chunks',
    'ha_personal_memories',
    'ha_rag_metrics',
    'ha_rag_feedback',
    'ha_tool_definitions',
    'ha_tool_activity_records',
    'ha_tool_idempotency_records',
    'ha_tool_pending_confirmations',
    'ha_telemetry_events',
    'ha_owner_scope_migration_activity'
  )
  and table_comment <> '';
```

Expected result: `commented_tables = 17`.

```sql
select table_name, column_name
from information_schema.columns
where table_schema = database()
  and table_name in (
    'ha_session_messages',
    'ha_security_activity',
    'ha_budget_counters',
    'ha_agent_state',
    'ha_snapshot_metadata',
    'ha_snapshot_content',
    'ha_knowledge_sources',
    'ha_knowledge_chunks',
    'ha_personal_memories',
    'ha_rag_metrics',
    'ha_rag_feedback',
    'ha_tool_definitions',
    'ha_tool_activity_records',
    'ha_tool_idempotency_records',
    'ha_tool_pending_confirmations',
    'ha_telemetry_events',
    'ha_owner_scope_migration_activity'
  )
  and (column_comment is null or column_comment = '')
order by table_name, ordinal_position;
```

Expected result: no rows. The H2 path is covered by `DurablePersistenceMigrationTest`; MySQL metadata still requires an environment-backed verification before production release.
