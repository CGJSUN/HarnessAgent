## Context

`enterprise-agent-platform` 已经定义了生产运行时、RAG、工具治理、安全治理和控制台能力，但当前代码实现仍以 `InMemorySessionStore`、`InMemoryKnowledgeStore`、`InMemoryToolStore`、`InMemoryRuntimeTelemetry`、`SecurityAuditService` 内部列表、`BudgetLimiter` 内部计数器和 `JsonSession` 本地目录为主。Redis、MySQL、snapshot store 和 OpenTelemetry 目前主要存在于配置字段、枚举、plan record、validator 和发布门禁文本中。

本变更要解决的是“生产契约”和“实际存储行为”之间的断层：生产多副本、审计留存、知识索引、工具幂等、预算限流、AgentScope 状态恢复和 sandbox snapshot 都不能继续依赖单进程内存或本地文件语义。

目标架构：

```text
Spring API / Services
        |
        v
Store Interfaces
  SessionStore | KnowledgeStore | ToolStore | AuditStore
  RuntimeTelemetry | BudgetCounterStore | AgentStateStore | SnapshotStore
        |
        v
Profile-aware Implementations
  dev/test: InMemory / LocalJson
  prod: MySQL/JDBC + Redis + Object/JDBC Snapshot + OTel
        |
        v
Durability / Recovery / Readiness Checks
```

## Goals / Non-Goals

**Goals:**

- 为生产 profile 提供真实可读写的持久化实现，并保留内存实现作为开发和单元测试默认实现。
- 保持现有 API 和 service 契约稳定，让持久化替换发生在 store、repository、factory 和 Spring bean selection 层。
- 覆盖会话、知识、工具、审计、幂等、遥测、预算计数、AgentScope 状态和 sandbox snapshot。
- 在启动期和发布门禁中验证真实持久化实现已启用、可达、schema 就绪，并且没有误用内存实现。
- 用集成测试证明重启恢复和跨实例读取，而不是只验证 plan 或 key 生成。

**Non-Goals:**

- 不重写聊天、RAG、工具治理、安全治理或控制台的业务 API。
- 不要求一次性接入所有云厂商对象存储；先定义 snapshot SPI，再实现首选后端。
- 不把内存实现删除；它们仍用于开发 profile、快速测试和无外部依赖的单元测试。
- 不迁移历史内存数据。内存数据在进程退出后本来不可恢复，只提供本地 JSON session 的有限迁移或兼容策略。

## Decisions

### Decision 1: 按存储语义拆分持久化 SPI，而不是做一个通用 KV

保留现有 `SessionStore`、`KnowledgeStore`、`ToolStore`、`RuntimeTelemetry` 接口，并为缺失的安全审计、预算计数、AgentScope 状态和 snapshot 增加专用接口。各 service 继续依赖语义化接口，生产实现通过 Spring profile 或 conditional bean 注入。

这样可以让查询、索引、留存、幂等和权限过滤各自使用合适的数据模型，避免把所有数据都塞进通用 KV 后再在业务层手写过滤。

替代方案：

- 单一 `DistributedStore` 承载所有内容：实现入口少，但审计搜索、RAG chunk 查询、幂等冲突和 telemetry 聚合会变成大量 ad hoc key 设计。
- 直接在 service 里使用 Redis/JDBC client：短期快，但会把持久化细节扩散到业务逻辑，后续难以测试和替换。

### Decision 2: MySQL/JDBC 承载长期业务记录，Redis 承载高频共享状态

生产默认建议用 MySQL/JDBC 存储需要留存、搜索和审计的数据：会话消息、知识源元数据、知识 chunk、工具注册表、工具审计、幂等记录、安全审计和 telemetry 事件归档。Redis 用于 AgentScope 短期状态、分布式预算计数、热点 session state 或需要低延迟共享的运行状态。

如果部署方选择 MySQL-only，需要提供等价的 AgentStateStore 和 BudgetCounterStore；如果选择 Redis-only，则审计和知识数据必须补足可查询、可备份、可留存的结构化方案，否则不能通过生产 readiness。

替代方案：

- Redis 存所有数据：低延迟，但审计留存、复杂查询、备份和 schema 演进成本较高。
- MySQL 存所有数据：一致性和审计友好，但高频计数和短期状态可能带来写放大。

### Decision 3: 生产启动必须验证“实现已接上”，不只验证配置值

`ProductionRuntimeValidator` 应从配置校验升级为运行时能力校验：检查 active bean 类型不是 `InMemory*`，检查 JDBC schema 或 Redis 连接可用，检查 snapshot store 可写，检查 OTel exporter 或 telemetry persistence 已启用。`ReleaseReadinessService` 不再硬编码 `PASSED`，而是读取 capability health。

替代方案：

- 继续只校验 `state-store.type` 和 URI：无法阻止配置写了 Redis 但代码仍使用本地 `JsonSession` 的情况。
- 只依赖部署文档约束：风险太高，发布门禁无法自动发现错误 wiring。

### Decision 4: AgentScope session/memory 由工厂接入真实 state store

`AgentSessionFactory` 不应固定创建 `JsonSession(properties.getState().getLocalDirectory())`。它应委托 `AgentStateStoreFactory` 根据 `StateStorePlan` 创建本地、Redis 或 MySQL-backed session/memory 适配器，并继续使用 `TenantStateKeyStrategy` 保证 key 包含 tenant/user/agent/session/scope。

替代方案：

- 只持久化平台自己的 `SessionStore`，不处理 AgentScope 状态：消息列表可恢复，但 Agent 记忆、压缩状态和工具上下文仍会跨副本丢失。
- 继续本地 JSON：适合开发，但不能满足多副本生产恢复。

### Decision 5: Snapshot store 作为 workspace/sandbox 状态的独立能力

Sandbox 和远程 workspace 的快照不应混入高频状态 store。定义 `SnapshotStore` 负责 `save/load/delete/list` 快照，生产实现可从 OSS、S3、MinIO 或 JDBC 中选择一个；`WorkspacePolicyService` 只负责选择策略，真实读写由 snapshot service 执行。

替代方案：

- 用对象存储承载所有状态：不适合高频小状态更新。
- 用 MySQL blob 承载所有快照：部署简单，但大文件、版本和生命周期管理不如对象存储自然。

### Decision 6: 测试必须证明恢复行为

新增持久化契约测试应使用同一接口创建两个 store 实例：实例 A 写入，实例 B 读取，模拟跨副本；同时覆盖重启后恢复、幂等冲突、审计留存、tenant 隔离、删除/撤销、预算计数和 snapshot round-trip。没有外部服务时可用 Testcontainers、嵌入式替身或 profile 分层运行。

替代方案：

- 只 mock repository：能测 service，但无法证明 schema、序列化和跨实例行为。
- 只测 validator：无法证明真实读写。

## Risks / Trade-offs

- [引入外部依赖导致本地开发变慢] -> 保留内存和本地实现，生产 profile 才强制 durable store。
- [MySQL schema 设计过早固化] -> 先围绕现有 domain model 建最小表结构，使用 migration 管理演进。
- [Redis 和 MySQL 双写不一致] -> 明确数据归属；长期记录以 MySQL/JDBC 为准，高频状态以 Redis 为准，避免同一事实双主写入。
- [审计数据量增长过快] -> 设计 tenant、时间、类型索引，并提供留存、归档和清理策略。
- [启动健康检查影响可用性] -> 生产 profile fail fast，开发 profile 降级；健康检查设置合理 timeout。
- [测试依赖外部服务不稳定] -> 将纯契约测试、集成测试和容器化测试分层，CI 可按环境选择执行。

## Migration Plan

1. 抽出缺失的 store SPI：安全审计、预算计数、AgentScope state 和 snapshot。
2. 增加 JDBC/MySQL schema、migration 和 repository，实现 session、knowledge、tool、audit、idempotency、telemetry 的 durable store。
3. 增加 Redis-backed state 和 budget counter，实现 AgentScope state factory 接入。
4. 增加 snapshot store SPI 和首个生产实现，并接入 workspace/sandbox 流程。
5. 更新 Spring bean selection：开发 profile 默认内存/本地，生产 profile 强制 durable implementations。
6. 更新 `ProductionRuntimeValidator`、health indicator 和 `ReleaseReadinessService`，让门禁基于真实能力状态。
7. 增加恢复、跨实例、schema 和发布门禁测试。
8. 部署时先在 staging 运行双实例恢复验证，再切生产 profile。

回滚策略：保留旧内存/本地实现用于开发和紧急单实例回退；生产回滚必须显式关闭多副本能力，并保留已写入 durable store 的审计和会话数据。schema migration 需要提供向后兼容窗口，避免回滚后无法读取核心数据。

## Open Questions

- 首个生产后端是 MySQL + Redis 组合，还是先实现 MySQL-only 路径？
- 是否允许使用 Testcontainers 作为持久化集成测试默认方案？
- AgentScope Java 当前是否已经暴露 Redis/MySQL session 或 memory adapter；如果没有，是实现适配器还是先包装本地 session 序列化？
- Telemetry 的生产要求是只导出到 OpenTelemetry，还是同时保留本地可查询事件表？
- Sandbox snapshot 首个目标后端选择 S3、MinIO、OSS 还是 JDBC？
