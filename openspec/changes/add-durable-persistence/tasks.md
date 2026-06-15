## 1. 基础依赖和配置

- [x] 1.1 增加 Redis、JDBC/MySQL、数据库迁移、可选 snapshot store 和 OpenTelemetry exporter 相关依赖。
- [x] 1.2 扩展 `ProductionRuntimeProperties`，补齐 durable store、schema、snapshot、telemetry、health check 和 retention 配置。
- [x] 1.3 定义 profile-aware store selection 规则，确保 development/test 可用内存实现，production 强制 durable implementation。
- [x] 1.4 增加本地开发、测试和生产示例配置，明确哪些配置只用于规划，哪些配置会驱动真实 store wiring。

## 2. 持久化 SPI 和数据模型

- [x] 2.1 为安全审计抽出 `SecurityAuditStore`，替代 `SecurityAuditService` 内部 `CopyOnWriteArrayList`。
- [x] 2.2 为预算计数抽出 `BudgetCounterStore`，替代 `BudgetLimiter` 内部 `ConcurrentHashMap`。
- [x] 2.3 定义 `AgentStateStore` 和 `AgentStateStoreFactory`，承接 AgentScope session、memory、compaction 和短期运行状态。
- [x] 2.4 定义 `SnapshotStore` 与 snapshot metadata model，覆盖保存、读取、列举、删除和后端位置。
- [x] 2.5 为会话、知识、工具、审计、幂等、telemetry 和 snapshot 设计最小数据库 schema 与 migration。

## 3. JDBC/MySQL 持久化实现

- [x] 3.1 实现 JDBC/MySQL-backed `SessionStore`，覆盖 append、list sessions、list messages 和 delete 行为。
- [x] 3.2 实现 JDBC/MySQL-backed `KnowledgeStore`，覆盖知识源、chunk、metric、feedback、删除和撤销行为。
- [x] 3.3 实现 JDBC/MySQL-backed `ToolStore`，覆盖工具注册、启用状态、工具审计和幂等记录。
- [x] 3.4 实现 JDBC/MySQL-backed `SecurityAuditStore`，覆盖审计记录写入、权限范围查询、留存窗口过滤。
- [x] 3.5 实现 JDBC/MySQL-backed telemetry store，用于需要本地查询的 API、Agent、模型、RAG、工具、Token、异常和反馈事件。
- [x] 3.6 为 JDBC/MySQL store 增加 tenant、user、agent、session、time 和 resource 维度索引。

## 4. Redis 和 AgentScope 状态接入

- [x] 4.1 实现 Redis-backed `AgentStateStore`，使用 `TenantStateKeyStrategy` 生成 tenant/user/agent/session/scope key。
- [x] 4.2 更新 `AgentSessionFactory`，根据 `StateStorePlan` 创建本地、Redis 或 MySQL/JDBC-backed AgentScope session/memory 适配器。
- [x] 4.3 实现 Redis-backed `BudgetCounterStore`，保证多副本共享请求数和 Token 消耗。
- [x] 4.4 在生产 profile 下阻止 Redis 或 MySQL 配置存在但 AgentScope 仍落到本地 `JsonSession` 的 wiring。
- [x] 4.5 为 Redis 写入失败、读取失败和连接不可达提供受控失败路径和可观测事件。

## 5. Snapshot 和 workspace 恢复

- [x] 5.1 实现首个生产 `SnapshotStore` 后端，支持 OSS、S3、MinIO 或 JDBC 中选定的一种。
- [x] 5.2 接入 workspace/sandbox 流程，在需要保存状态时写入 snapshot metadata 和快照内容。
- [x] 5.3 支持从 snapshot store 恢复 sandbox 或远程 workspace 状态，并执行租户、Agent、会话权限校验。
- [x] 5.4 在 snapshot store 不可写或不可读时让 production readiness 失败，并返回明确诊断信息。

## 6. 生产校验和发布门禁

- [x] 6.1 将 `ProductionRuntimeValidator` 从配置校验扩展为能力校验，检查 active bean 类型、连接可达性、schema 和 snapshot 后端状态。
- [x] 6.2 增加 durable persistence health indicator，暴露 store、snapshot、telemetry、budget counter 和 AgentScope state 的健康状态。
- [x] 6.3 更新 `ReleaseReadinessService`，使 `distributed-state`、`audit`、`telemetry`、`budget` 和 `snapshot` gate 基于真实健康状态计算。
- [x] 6.4 确保 production profile 下 telemetry 至少启用 OpenTelemetry export 或 durable telemetry store。
- [x] 6.5 确保生产 readiness 不再硬编码 `PASSED`，并能报告具体失败原因。

## 7. 持久化契约和集成测试

- [x] 7.1 为 `SessionStore` 增加跨实例读写、重启恢复、删除和租户隔离契约测试。
- [x] 7.2 为 `KnowledgeStore` 增加知识源、chunk、metric、feedback、删除、撤销和重启恢复测试。
- [x] 7.3 为 `ToolStore` 和 `SecurityAuditStore` 增加审计留存、脱敏、权限查询、幂等冲突和跨实例测试。
- [x] 7.4 为 `BudgetCounterStore` 增加多副本共享计数、超限拒绝和计数写入失败测试。
- [x] 7.5 为 `AgentStateStore` 增加副本切换恢复、key 隔离和本地 `JsonSession` 禁用测试。
- [x] 7.6 为 `SnapshotStore` 增加 save/load/list/delete、权限校验和不可用后端测试。
- [x] 7.7 为生产 readiness 和发布门禁增加失败场景测试，覆盖 Redis 配置未接入、schema 缺失、telemetry 未配置和 snapshot 不可写。

## 8. 文档和运维说明

- [x] 8.1 更新启动文档，说明 development/test/production 的 store selection 行为。
- [x] 8.2 增加 MySQL/JDBC schema 初始化、migration、索引和备份恢复说明。
- [x] 8.3 增加 Redis、snapshot store 和 OpenTelemetry 的生产配置示例。
- [x] 8.4 增加生产发布前的双实例恢复验证步骤和回滚注意事项。
