## ADDED Requirements

### Requirement: 生产环境分布式状态
生产环境和多副本部署中，系统 SHALL 使用 RedisDistributedStore 或 MysqlDistributedStore 等 DistributedStore。

#### Scenario: 运行多个服务副本
- **WHEN** 平台以多个服务副本部署
- **THEN** 系统 SHALL 使用分布式状态存储
- **AND** 系统 SHALL NOT 依赖本地 JsonFileAgentStateStore 作为共享运行状态

### Requirement: 租户感知状态 key
系统 SHALL 在状态 key 设计中包含租户、用户、Agent、会话和隔离范围。

#### Scenario: 副本切换后恢复会话
- **WHEN** 用户请求被不同于上一请求的服务副本处理
- **THEN** 系统 SHALL 恢复该租户、用户、Agent 和会话对应的正确状态

### Requirement: 工作区策略
系统 SHALL 按工作负载风险和生产 profile 选择工作区实现。

#### Scenario: 生产环境办公类 Agent
- **WHEN** Agent 不执行不可信代码或 Shell 命令
- **THEN** 系统 SHALL 使用适合共享生产访问的远程工作区策略

#### Scenario: 生产环境代码执行 Agent
- **WHEN** Agent 执行代码、Shell、SQL 或不可信脚本
- **THEN** 系统 SHALL 使用带隔离控制的沙箱工作区

### Requirement: 沙箱快照策略
生产沙箱 SHALL 配置快照存储，用于恢复、审计或任务交接。

#### Scenario: 持久化沙箱状态
- **WHEN** 沙箱任务需要保存状态
- **THEN** 系统 SHALL 将快照写入已配置的 OSS、S3、MinIO 或 JDBC 快照存储

### Requirement: OpenTelemetry 可观测性
系统 SHALL 为 API 调用、模型调用、RAG 检索、工具调用、Token 用量、耗时、异常和用户反馈输出 traces 或 metrics。

#### Scenario: 排查慢回答
- **WHEN** 回答耗时过长或失败
- **THEN** 运维人员 SHALL 能够追踪该请求在 API、Agent、模型、RAG 和工具链路中的跨度，前提是这些组件参与了执行

### Requirement: 限流和预算
系统 SHALL 按租户、用户、Agent 和模型提供方执行限流或预算控制。

#### Scenario: 租户超过 Token 预算
- **WHEN** 租户超过配置的预算或速率限制
- **THEN** 系统 SHALL 按策略限流或拒绝新的请求

### Requirement: 模型 fallback
系统 SHALL 在模型提供方不可用或失败时支持已配置的 fallback 行为。

#### Scenario: 主模型提供方失败
- **WHEN** 主模型提供方返回可重试失败
- **THEN** 系统 SHALL 应用已配置的 fallback 提供方或失败响应策略

### Requirement: 长耗时工作保护
系统 SHALL 防止长时间流式响应、工具调用或沙箱执行耗尽服务容量。

#### Scenario: 工具执行超时
- **WHEN** 工具或沙箱执行超过配置超时时间
- **THEN** 系统 SHALL 按策略取消或隔离该工作，并返回受控失败
