## Why

当前平台已经暴露 Redis、MySQL、快照和 telemetry 等生产配置，但运行时仍把会话、知识库、工具、审计、遥测、预算计数和 AgentScope 状态存放在进程内存或本地 JSON 中。本变更用于补齐真实持久化能力，让生产部署能够跨重启恢复、支持多副本扩展，并证明审计与运营记录真正可留存。

## What Changes

- 将 durable persistence 定义为显式平台能力，不再把存储配置和 validator 视为生产持久化已经完成。
- 为会话历史、知识源与切片、工具注册表、工具审计、幂等记录、安全审计、遥测事件和预算计数提供真实后端存储。
- 将已配置的 Redis 或 MySQL state store 接入 AgentScope session 和 memory 状态，而不是只把 store 类型拼进 key。
- 在生产 sandbox profile 需要 OSS、S3、MinIO 或 JDBC-backed snapshot 时，提供真实快照读写实现。
- 增加启动期校验：生产 profile 下，必需的 durable store 未配置、不可达或未被 Spring wiring 选中时，应用应失败启动。
- 增加恢复和多副本测试，验证一个实例写入的状态可在重启后或另一个实例中读取。
- 更新发布门禁，使 `distributed-state`、审计留存、telemetry 和 snapshot 只有在接入真实持久化实现后才能通过。

不计划引入破坏性 API 变更。现有 controller 和 service 契约应保持稳定，存储实现改为按 profile 选择并支持真实持久化。

## Capabilities

### New Capabilities

- `durable-persistence`: 持久化后端、按 profile 选择 store、启动期校验、跨副本恢复、审计留存、telemetry 导出/持久化、分布式预算计数、AgentScope 状态接入和 sandbox snapshot 持久化。

### Modified Capabilities

- 无。当前仓库没有已归档的 `openspec/specs/` 主线能力；本变更新增横切能力来补齐并约束已完成平台变更中尚未真正落地的持久化行为。

## Impact

- 影响后端包：`session`、`rag`、`tooling`、`security`、`production`、`agent`、`console` 和 `release`。
- 影响运行时行为：Spring store bean 选择、生产 profile 校验、AgentScope session 创建、telemetry 记录、预算限制、审计留存和 workspace/sandbox snapshot 恢复。
- 影响依赖：Redis client 和/或 JDBC/MySQL driver、数据库迁移工具或 schema 初始化、可选对象存储 client，以及 OpenTelemetry exporter。
- 影响测试：持久化契约测试、repository/store 集成测试、重启与跨实例恢复测试、生产 readiness 测试和发布门禁测试。
- 影响运维：生产配置必须包含真实持久化端点、凭证、schema/migration 归属、健康检查、留存策略和备份恢复流程。
