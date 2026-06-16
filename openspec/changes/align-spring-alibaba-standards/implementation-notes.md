## Baseline Inventory

### Package Migration Boundaries

`HarnessAgentApplication` remains in `com.harnessagent`; `@SpringBootApplication` does not declare a custom scan root. The backend remains a single Maven module.

Target package boundaries for the current Java classes:

- `api.controller`: REST controllers currently in `api`.
- `api.request`: top-level API request records.
- `api.response`: top-level API response records and extracted nested response records.
- `console.application`: `ConsoleService` and console query/filter inputs.
- `console.view`: read-only console view records.
- `production.config`: runtime properties, profile, workload type.
- `production.health`: readiness/health/guard/validator services.
- `production.state`: AgentScope state abstractions and keys.
- `production.budget`: budget counter abstractions and limiter.
- `production.telemetry`: telemetry API and event records.
- `production.snapshot`: snapshot abstractions, metadata, and plans.
- `production.workspace`: workspace policy and snapshot orchestration.
- `production.infrastructure`: JDBC/Redis/InMemory implementations and runtime infrastructure helpers.
- `tooling.domain`: tool definitions, policies, principals, risk/source/status/value records.
- `tooling.application`: tool application service.
- `tooling.execution`: executors, execution command, and execution result.
- `tooling.persistence`: store interface, JDBC/InMemory stores, and idempotency records.
- `tooling.audit`: tool audit record.
- `rag.domain`: knowledge source/chunk/citation/feedback/metric/principal/domain records.
- `rag.application`: knowledge application service, document input, retrieval policy, tokenizer/chunker.
- `rag.retrieval`: retrieval result.
- `rag.persistence`: knowledge store interface and implementations.
- `security.domain`: identity, permission, protected resource, decisions, skill status/version.
- `security.application`: authorization, identity, data permission, prompt guard, audit service, skill governance, redaction.
- `security.persistence`: audit store/record and secret store implementations.
- `chat.domain`: chat command/result.
- `chat.application`: chat service.
- `orchestration.domain`: orchestration request/result/trace/route/handoff/value records.
- `orchestration.application`: orchestration service, router, registry.
- `agent.runtime`: runtime interfaces, requests, replies, and events.
- `agent.application`: AgentScope adapter and session factory.
- `session.domain`: chat message, role, session summary.
- `session.persistence`: session store interface and implementations.

Riskier service classes are moved only as package/import changes, without changing governance methods: `ChatService`, `ToolService`, `KnowledgeService`, `OrchestrationService`, `EnterpriseHarnessAgentRuntime`, `AgentSessionFactory`, `DurablePersistenceHealthService`, `ProductionRuntimeValidator`, `WorkspaceSnapshotService`, `BudgetLimiter`, and `RuntimeTimeoutGuard`.

### API Contract Inventory

Protected REST roots and endpoints:

- `/api/chat`: `POST /api/chat`, `POST /api/chat/stream` with `text/event-stream`.
- `/api/tools`: `POST /api/tools`, `GET /api/tools`, `POST /api/tools/execute`, `POST /api/tools/reject`, `GET /api/tools/audit`.
- `/api/console`: user, agents, tools, knowledge, skills, metrics, cost, and audit endpoints.
- `/api/knowledge`: source, document, retrieve, metrics, feedback, revoke, and delete endpoints.
- `/api`: `GET /api/sessions`, `GET /api/messages`, `DELETE /api/sessions/{sessionId}`.
- `/api/release`: scenario, phase-gates, rollback, and acceptance endpoints.
- `/api/orchestration`: agents, route, agent-as-tool, and traces endpoints.

`/api/chat/stream` emits lower-case runtime event names: `status`, `delta`, `tool`, `error`, and `done`. `StreamEventResponse` keeps `type`, `content`, `terminal`, `noAnswerReason`, `citations`, and `metadata`.

Backend contract coverage added in `ApiContractTest`: URL annotations, chat request-to-command identity handling, chat JSON field shape, stream payload JSON field shape, and 400/409 error envelope status/body shape.

### Governance Order Baseline

Must preserve these call-order constraints:

- Chat/RAG: runtime context scope, prompt safety, budget, user message persistence, optional RAG, no-answer short-circuit, model runtime, assistant message persistence, telemetry.
- Tooling: tool lookup, enabled/tenant/permission/schema/prompt-safety/idempotency preflight, high-risk pending approval, idempotency reuse/conflict, timeout guarded execution, idempotency save, audit, telemetry.
- Production readiness: runtime config validation, durable store wiring checks, schema/table checks, snapshot writability, release gate mapping.
- Orchestration: route decision, redacted shared context, child runtime context, child agent execution, trace/handoff recording, telemetry.

Minimum focused backend test set for package moves:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.18.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.0.18.jdk/Contents/Home/bin:$PATH mvn -Dtest=ApiContractTest,ApiIdentityResolverTest,ChatServiceTest,KnowledgeServiceTest,ToolServiceTest,ProductionRuntimeTest,DurablePersistenceHealthServiceTest,WorkspaceSnapshotServiceTest,ReleaseReadinessServiceTest,OrchestrationServiceTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.18.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.0.18.jdk/Contents/Home/bin:$PATH mvn -Dtest=JdbcSessionStoreTest,JdbcKnowledgeStoreTest,JdbcToolStoreTest,JdbcSecurityAuditStoreTest,JdbcBudgetCounterStoreTest,JdbcAgentStateStoreTest,JdbcSnapshotStoreTest,JdbcRuntimeTelemetryTest test
```

### Dependency Baseline

`pom.xml` has no Lombok, MapStruct, Maven module split, or alternate logging framework. The existing logging stack uses SLF4J/Logback aligned with Spring Boot dependency management. Baseline tests pass with JDK 17; Maven running on Java 8 fails because test classes target class file version 61.

## Durable Persistence Metadata

The durable schema keeps `src/main/resources/db/migration/V1__durable_persistence.sql` unchanged. Metadata comments are added through vendor-specific V2 scripts outside the common migration root so Flyway does not recursively load both vendors:

- H2: `src/main/resources/db/vendor-migration/h2/V2__durable_persistence_comments.sql`
- MySQL: `src/main/resources/db/vendor-migration/mysql/V2__durable_persistence_comments.sql`

Runtime locations are `classpath:db/migration,classpath:db/vendor-migration/{vendor}`. The table and field dictionary is documented in `docs/durable-persistence-schema.md`; it covers 14 durable tables and 126 columns, including tenant/user/agent/session isolation fields, JSON text fields, governance fields, RAG fields, and production state/snapshot fields.

Validation added:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.18.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.0.18.jdk/Contents/Home/bin:$PATH mvn -Dtest=DurablePersistenceMigrationTest test
```

The test applies common V1 plus H2 V2 through Flyway and checks H2 metadata remarks for all 14 tables and 126 columns. Existing JDBC store tests that load V1 directly remain unchanged and continue to pass.

Known release follow-up:

- MySQL V2 syntax is authored from the V1 column definitions but has not been executed against a live MySQL instance in this workspace.
- Before production rollout, run the `information_schema` checks in `docs/durable-persistence-schema.md` and have the DBA or schema owner confirm `MODIFY COLUMN ... COMMENT` did not alter type, nullability, `AUTO_INCREMENT`, or index semantics.
- H2 remarks are verified through JDBC `DatabaseMetaData`; direct `INFORMATION_SCHEMA` column names vary under `DATABASE_TO_UPPER=false`, so the test intentionally avoids raw H2 system-table SQL.
