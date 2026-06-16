# Durable Persistence Schema

This document describes the durable persistence schema created by `V1__durable_persistence.sql` and documented by vendor-specific V2 comment migrations.

Flyway locations:

- Common DDL: `classpath:db/migration`
- Vendor comments: `classpath:db/vendor-migration/{vendor}`
- H2 comments: `src/main/resources/db/vendor-migration/h2/V2__durable_persistence_comments.sql`
- MySQL comments: `src/main/resources/db/vendor-migration/mysql/V2__durable_persistence_comments.sql`

The V2 scripts add metadata only. They must not store prompt text, retrieval evidence, tool payloads, DSNs, Redis URIs, snapshot payloads, or workspace file data in comments.

## Table Dictionary

| Table | Purpose | Primary Access Dimensions |
|---|---|---|
| `ha_session_messages` | Session message history. | `tenant_id`, `user_id`, `agent_id`, `session_id`, `created_at` |
| `ha_security_audit` | Security and authorization audit events. | `tenant_id`, `user_id`, `resource_type`, `resource_id`, `occurred_at` |
| `ha_budget_counters` | Request and token budget counters. | `tenant_id`, `user_id`, `agent_id`, `resource_id`, `updated_at` |
| `ha_agent_state` | Durable AgentScope state. | `tenant_id`, `user_id`, `agent_id`, `session_id`, `scope`, `updated_at` |
| `ha_snapshot_metadata` | Workspace snapshot metadata. | `tenant_id`, `agent_id`, `session_id`, `task_id`, `created_at` |
| `ha_snapshot_content` | Snapshot artifact payload linked to metadata. | `snapshot_id` |
| `ha_knowledge_sources` | RAG knowledge source metadata and access policy. | `tenant_id`, `owner_id`, `updated_at` |
| `ha_knowledge_chunks` | Searchable chunks derived from governed sources. | `tenant_id`, `source_id`, `chunk_index` |
| `ha_rag_metrics` | RAG hit, permission filtering, and no-answer metrics. | `tenant_id`, `user_id`, `created_at` |
| `ha_rag_feedback` | User feedback on RAG answers. | `tenant_id`, `user_id`, `created_at` |
| `ha_tool_definitions` | Governed tool registry. | `tenant_id`, `name`, `owner_system`, `owner_id` |
| `ha_tool_audit_records` | Tool execution audit records. | `tenant_id`, `user_id`, `tool_id`, `occurred_at` |
| `ha_tool_idempotency_records` | Idempotency records for mutating tool execution. | `idempotency_key` |
| `ha_telemetry_events` | Runtime telemetry events. | `tenant_id`, `user_id`, `agent_id`, `occurred_at` |

## Field Dictionary

| Table | Fields |
|---|---|
| `ha_session_messages` | `id`: message id; `tenant_id`: tenant isolation key; `user_id`: user isolation key; `agent_id`: agent isolation key; `session_id`: conversation isolation key; `role`: message role; `content`: persisted message text; `created_at`: creation timestamp. |
| `ha_security_audit` | `id`: audit id; `occurred_at`: event timestamp; `tenant_id`: tenant isolation key; `user_id`: user associated with the action; `resource_type`: governed resource category; `resource_id`: governed resource id; `action`: audited action; `details_json`: structured audit details JSON text. |
| `ha_budget_counters` | `counter_key`: stable counter key; `tenant_id`: tenant isolation key; `user_id`: user isolation key; `agent_id`: agent isolation key; `resource_id`: budgeted provider or resource; `requests`: consumed requests; `tokens`: consumed token estimate; `updated_at`: last update timestamp. |
| `ha_agent_state` | `state_key`: stable state key; `tenant_id`: tenant isolation key; `user_id`: user isolation key; `agent_id`: agent isolation key; `session_id`: session isolation key; `scope`: AgentScope memory or state scope; `state_value`: serialized state text; `updated_at`: last update timestamp. |
| `ha_snapshot_metadata` | `id`: snapshot id; `tenant_id`: tenant isolation key; `agent_id`: agent isolation key; `session_id`: session isolation key; `task_id`: task or run id; `created_at`: snapshot timestamp; `backend_type`: storage backend type; `location`: opaque storage reference. |
| `ha_snapshot_content` | `snapshot_id`: snapshot metadata id and primary key; `content`: snapshot artifact payload. |
| `ha_knowledge_sources` | `id`: knowledge source id; `tenant_id`: tenant isolation key; `owner_id`: owner user id; `title`: display title; `version`: version label; `visibility`: visibility policy; `allowed_departments_json`: allowed departments JSON text; `allowed_roles_json`: allowed roles JSON text; `allowed_users_json`: allowed users JSON text; `update_policy`: update policy; `status`: lifecycle status; `created_at`: creation timestamp; `updated_at`: update timestamp. |
| `ha_knowledge_chunks` | `id`: chunk id; `source_id`: source id; `tenant_id`: tenant isolation key; `title`: source title copied for citation; `version`: source version copied for citation; `chunk_index`: order within source; `content`: chunk text used after permission filtering; `tokens_json`: search tokens JSON text. |
| `ha_rag_metrics` | `id`: metric id; `tenant_id`: tenant isolation key; `user_id`: retrieval user; `query_text`: original retrieval query for governed analytics; `hit`: whether accessible evidence was found; `candidate_count`: candidate chunks before permission filtering; `permitted_count`: chunks after permission filtering; `failure_reason`: no-answer or retrieval failure code; `created_at`: metric timestamp. |
| `ha_rag_feedback` | `id`: feedback id; `tenant_id`: tenant isolation key; `user_id`: feedback user; `query_text`: retrieval query associated with feedback; `helpful`: helpful marker; `comment_text`: optional feedback comment; `created_at`: feedback timestamp. |
| `ha_tool_definitions` | `id`: tool id; `tenant_id`: tenant isolation key; `name`: tool name; `description`: purpose description; `owner_system`: owning system; `owner_id`: owner user or service id; `source_type`: source type; `source_ref`: opaque source reference; `risk_level`: governance risk; `mutating`: whether execution can mutate state; `enabled`: execution enabled flag; `parameter_schema_json`: parameter schema JSON text; `permission_policy_json`: permission policy JSON text; `audit_policy_json`: audit policy JSON text; `created_at`: registration timestamp; `updated_at`: update timestamp. |
| `ha_tool_audit_records` | `id`: audit id; `occurred_at`: execution timestamp; `tenant_id`: tenant isolation key; `user_id`: user isolation key; `agent_id`: agent isolation key; `session_id`: session isolation key; `tool_id`: tool id; `tool_name`: tool name at execution time; `source_type`: source type at execution time; `risk_level`: risk level at execution time; `status`: execution status; `sanitized_input_json`: sanitized input JSON text; `sanitized_output_json`: sanitized output JSON text; `duration_millis`: duration in milliseconds; `approval_id`: approval id when present; `reviewer_id`: reviewer id when present; `idempotency_key`: caller idempotency key; `failure_reason`: denial or failure reason. |
| `ha_tool_idempotency_records` | `idempotency_key`: stable idempotency key; `parameter_fingerprint`: canonical parameter fingerprint; `result_json`: stored execution result JSON text; `created_at`: record creation timestamp. |
| `ha_telemetry_events` | `id`: telemetry event id; `occurred_at`: event timestamp; `type`: event type; `tenant_id`: tenant isolation key; `user_id`: user isolation key; `agent_id`: agent isolation key; `component`: emitting component; `duration_millis`: observed duration; `attributes_json`: event attributes JSON text. |

## MySQL Metadata Verification

Run these checks after applying migrations in a MySQL schema:

```sql
select count(*) as commented_tables
from information_schema.tables
where table_schema = database()
  and table_name in (
    'ha_session_messages',
    'ha_security_audit',
    'ha_budget_counters',
    'ha_agent_state',
    'ha_snapshot_metadata',
    'ha_snapshot_content',
    'ha_knowledge_sources',
    'ha_knowledge_chunks',
    'ha_rag_metrics',
    'ha_rag_feedback',
    'ha_tool_definitions',
    'ha_tool_audit_records',
    'ha_tool_idempotency_records',
    'ha_telemetry_events'
  )
  and table_comment <> '';
```

Expected result: `commented_tables = 14`.

```sql
select table_name, column_name
from information_schema.columns
where table_schema = database()
  and table_name in (
    'ha_session_messages',
    'ha_security_audit',
    'ha_budget_counters',
    'ha_agent_state',
    'ha_snapshot_metadata',
    'ha_snapshot_content',
    'ha_knowledge_sources',
    'ha_knowledge_chunks',
    'ha_rag_metrics',
    'ha_rag_feedback',
    'ha_tool_definitions',
    'ha_tool_audit_records',
    'ha_tool_idempotency_records',
    'ha_telemetry_events'
  )
  and (column_comment is null or column_comment = '')
order by table_name, ordinal_position;
```

Expected result: no rows. The H2 path is covered by `DurablePersistenceMigrationTest`; MySQL metadata still requires an environment-backed verification before production release.
