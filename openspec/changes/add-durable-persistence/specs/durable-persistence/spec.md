## ADDED Requirements

### Requirement: Profile 感知的持久化实现选择
系统 SHALL 按运行 profile 选择存储实现：开发和测试 profile MAY 使用内存或本地文件实现，生产 profile SHALL 使用真实 durable store，并且 SHALL NOT 将 `InMemorySessionStore`、`InMemoryKnowledgeStore`、`InMemoryToolStore`、`InMemoryRuntimeTelemetry` 或本地 `JsonSession` 作为共享生产状态。

#### Scenario: 开发环境使用内存实现
- **WHEN** 平台以 development profile 启动且未配置外部 durable store
- **THEN** 系统 MAY 使用内存或本地文件 store 支持本地调试
- **AND** 系统 SHALL 标记该运行模式不满足生产持久化要求

#### Scenario: 生产环境拒绝内存实现
- **WHEN** 平台以 production profile 启动
- **THEN** 系统 SHALL 选择 Redis、MySQL/JDBC 或已批准对象存储等真实 durable implementation
- **AND** 系统 SHALL 在检测到共享状态仍由内存或本地文件实现承载时失败启动

### Requirement: 业务记录持久化
系统 SHALL 持久化会话历史、知识源、知识切片、RAG 指标、RAG 反馈、工具注册表、工具审计、工具幂等记录和安全审计记录，并按租户、用户、Agent、会话和时间维度提供可查询能力。

#### Scenario: 重启后恢复会话历史
- **WHEN** 用户在生产 profile 下完成一次聊天并重启应用实例
- **THEN** 系统 SHALL 能够通过会话和消息 API 读取重启前写入的会话历史
- **AND** 返回结果 SHALL 仍受租户、用户和 Agent 隔离约束

#### Scenario: 重启后保留知识索引
- **WHEN** 授权用户上传并索引知识文档后应用实例重启
- **THEN** 系统 SHALL 保留知识源元数据、文档切片、版本、权限元数据和检索指标
- **AND** 后续 RAG 检索 SHALL NOT 依赖重启前的进程内集合

#### Scenario: 工具幂等记录跨实例生效
- **WHEN** 一个服务实例执行会修改外部系统的工具并写入幂等记录
- **THEN** 另一个服务实例 SHALL 能够识别相同幂等 key 的重复请求
- **AND** 系统 SHALL 返回重复结果或幂等冲突，而不是再次执行外部变更

### Requirement: AgentScope 状态持久化
系统 SHALL 将配置的 Redis 或 MySQL/JDBC state store 接入 AgentScope session、memory、compaction 和运行状态，生产多副本部署中 SHALL 使用包含 tenant、user、agent、session 和 scope 的 key 恢复同一会话状态。

#### Scenario: 副本切换后恢复 Agent 状态
- **WHEN** 同一租户、用户、Agent 和会话的连续请求由不同服务副本处理
- **THEN** 后续副本 SHALL 读取前一副本写入的 AgentScope session 或 memory 状态
- **AND** 系统 SHALL NOT 只使用本地 `JsonSession` 目录恢复生产状态

#### Scenario: 状态 key 包含隔离维度
- **WHEN** 系统写入 AgentScope 状态、预算计数或短期运行状态
- **THEN** 存储 key SHALL 包含租户、用户、Agent、会话和隔离范围
- **AND** 不同租户或不同用户的状态 SHALL NOT 发生覆盖或串读

### Requirement: 审计留存和脱敏持久化
系统 SHALL 持久化脱敏后的工具审计、安全审计、用户确认、审核人审批、人工干预和高风险操作记录，并按配置的留存策略控制查询、归档和清理。

#### Scenario: 审计人员查询历史工具调用
- **WHEN** 审计人员在审计权限范围内查询已完成的工具调用
- **THEN** 系统 SHALL 返回持久化的工具名称、状态、租户、用户、Agent、会话、脱敏输入、脱敏输出、耗时、审批人和时间戳
- **AND** 系统 SHALL NOT 返回未脱敏的敏感参数或凭证

#### Scenario: 留存窗口过滤审计记录
- **WHEN** 审计记录早于当前租户或系统配置的留存窗口
- **THEN** 系统 SHALL 在默认审计查询中排除该记录或标记为已归档
- **AND** 清理或归档行为 SHALL NOT 影响留存窗口内记录的可查询性

### Requirement: 分布式预算和限流计数
系统 SHALL 在生产 profile 下使用 durable 或 distributed counter 记录租户、用户、Agent 和模型提供方的预算与限流消耗，多个服务副本 SHALL 共享同一计数视图。

#### Scenario: 多副本共享 Token 预算
- **WHEN** 同一租户的请求分布到多个服务副本
- **THEN** 系统 SHALL 使用共享计数器累计请求数和 Token 消耗
- **AND** 任一副本 SHALL 在预算超限时拒绝新的请求

#### Scenario: 计数写入失败时拒绝请求
- **WHEN** 生产 profile 下预算计数 store 不可写
- **THEN** 系统 SHALL 返回受控失败并拒绝消耗预算的请求
- **AND** 系统 SHALL 记录可观测事件用于排查

### Requirement: Telemetry 持久化和导出
系统 SHALL 为 API、Agent、模型、RAG、工具、Token、耗时、异常和反馈事件提供生产级 telemetry 输出，生产 profile SHALL 至少启用 OpenTelemetry export 或 durable telemetry store 中的一种。

#### Scenario: 慢请求可跨重启追踪
- **WHEN** 生产环境发生一次慢回答或工具调用失败
- **THEN** 系统 SHALL 输出可关联 tenant、user、agent、session 和 component 的 trace 或 metric
- **AND** 运维人员 SHALL 能够在应用实例重启后继续查询或从外部 telemetry 系统查看该事件

#### Scenario: Telemetry 未配置时生产启动失败
- **WHEN** production profile 未启用 OpenTelemetry exporter 且未配置 durable telemetry store
- **THEN** 系统 SHALL 在 readiness 校验中标记 telemetry gate 失败
- **AND** 发布门禁 SHALL NOT 将生产运行时标记为通过

### Requirement: Sandbox 和 workspace snapshot 持久化
系统 SHALL 为生产 sandbox 或远程 workspace 状态提供 snapshot store，支持保存、读取、列举和删除快照，并在配置要求时写入 OSS、S3、MinIO 或 JDBC-backed snapshot store。

#### Scenario: 保存 sandbox 快照
- **WHEN** 生产环境中 sandbox 任务需要保存工作区状态
- **THEN** 系统 SHALL 将快照写入已配置的 snapshot store
- **AND** 快照 SHALL 记录租户、Agent、会话、任务标识、创建时间和后端位置

#### Scenario: 从快照恢复工作区
- **WHEN** sandbox 任务需要在另一个服务实例或后续执行中恢复
- **THEN** 系统 SHALL 能够从 snapshot store 读取对应快照
- **AND** 系统 SHALL 在权限校验通过后恢复到正确工作区范围

### Requirement: 生产 readiness 持久化校验
系统 SHALL 在启动期、健康检查和发布门禁中校验 durable persistence 能力，只有真实 store 可达、schema 或 bucket 就绪、active bean 类型正确、恢复测试通过时，相关 gate 才能通过。

#### Scenario: Redis 配置存在但未接入实现
- **WHEN** production profile 配置了 Redis URI 但 active Agent state 仍使用本地 `JsonSession`
- **THEN** 系统 SHALL 将 distributed-state readiness 标记为失败
- **AND** 发布门禁 SHALL 报告具体失败原因

#### Scenario: 数据库 schema 缺失
- **WHEN** production profile 选择 MySQL/JDBC durable store 但必需 schema 或 migration 未初始化
- **THEN** 系统 SHALL 在启动期或 readiness 检查中失败
- **AND** 错误信息 SHALL 指出缺失的 store 或 schema 类别

#### Scenario: 发布门禁基于真实能力状态
- **WHEN** 运营人员查看发布 readiness
- **THEN** 系统 SHALL 基于 durable store、snapshot store、telemetry、budget counter 和 AgentScope state 的实际健康状态计算 gate
- **AND** 系统 SHALL NOT 硬编码生产运行时 gate 为通过

### Requirement: 持久化契约测试
系统 SHALL 为每类 durable store 提供契约测试，验证写入、读取、删除、租户隔离、跨实例读取、重启恢复、幂等冲突、审计留存和错误处理。

#### Scenario: 跨实例读取测试
- **WHEN** 测试使用两个独立 store 实例连接同一个后端
- **THEN** 实例 B SHALL 能够读取实例 A 写入的记录
- **AND** 测试 SHALL 覆盖会话、知识、工具审计、幂等记录、预算计数和 AgentScope state 中的关键路径

#### Scenario: Store 不可用测试
- **WHEN** durable store 在写入或读取期间不可用
- **THEN** 系统 SHALL 返回受控失败或进入明确降级路径
- **AND** 生产 profile SHALL NOT 静默回退到内存实现
