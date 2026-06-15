## MODIFIED Requirements

### Requirement: 生产环境分布式状态
生产环境和多副本部署中，系统 SHALL 使用真实持久化或分布式状态实现。当前可验收边界为：session、knowledge、tool、audit 和 telemetry 的长期业务记录以 MySQL/JDBC 为主；Redis 当前 SHALL 只被声明为 AgentScope state 和 budget counter 的可选生产实现，除非后续变更补齐 Redis 业务 store。

#### Scenario: 运行多个服务副本
- **WHEN** 平台以多个服务副本部署
- **THEN** 系统 SHALL 使用已接线的共享状态或持久化存储
- **AND** 系统 SHALL NOT 依赖本地 JsonFileAgentStateStore 作为共享运行状态

#### Scenario: Redis 只覆盖部分共享状态
- **WHEN** 文档说明 Redis-backed production state
- **THEN** 文档 SHALL 说明 Redis 当前覆盖 AgentScope state 和 budget counter
- **AND** 文档 SHALL NOT 暗示 session、knowledge、tool、audit 或 telemetry 业务 store 已经存在 Redis 生产实现

### Requirement: 沙箱快照策略
生产沙箱 SHALL 配置已接线的快照存储，用于恢复、审计或任务交接。当前可验收的生产 snapshot 后端 SHALL 是 JDBC；OSS、S3 和 MinIO SHALL 被描述为后续对象存储扩展入口，直到真实实现和健康检查完成。

#### Scenario: 持久化沙箱状态
- **WHEN** 沙箱任务需要保存状态
- **THEN** 系统 SHALL 将快照写入当前已接线的 snapshot store
- **AND** 文档 SHALL 使用 JDBC snapshot 作为当前生产验收路径

#### Scenario: 对象存储 snapshot 未接线
- **WHEN** 运维人员只配置 OSS、S3 或 MinIO snapshot 类型但没有对应生产实现
- **THEN** 系统 SHALL NOT 将该部署判定为 snapshot production ready
- **AND** 文档 SHALL 指向后续对象存储 snapshot 变更
