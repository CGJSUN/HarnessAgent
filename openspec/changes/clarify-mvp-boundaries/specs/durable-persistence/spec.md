## MODIFIED Requirements

### Requirement: Profile 感知的持久化实现选择
系统 SHALL 按运行 profile 选择存储实现：开发和测试 profile MAY 使用内存、H2 或本地文件实现；生产 profile SHALL 使用已接线的真实 durable store。当前生产边界为 MySQL/JDBC 承载 session、knowledge、tool、audit 和 telemetry 等长期业务记录，Redis 承载 AgentScope state 和 budget counter，对象存储后端只在已有真实实现和健康检查时才能声明 production ready。

#### Scenario: 开发环境使用内存实现
- **WHEN** 平台以 development profile 启动且未配置外部 durable store
- **THEN** 系统 MAY 使用内存、H2 或本地文件 store 支持本地调试
- **AND** 系统 SHALL 标记该运行模式不满足生产持久化要求

#### Scenario: 生产环境拒绝内存实现
- **WHEN** 平台以 production profile 启动
- **THEN** 系统 SHALL 选择当前已接线的 MySQL/JDBC、Redis AgentScope state、Redis budget counter 或已批准 snapshot store 实现
- **AND** 系统 SHALL 在检测到共享状态仍由内存或本地文件实现承载时失败启动

#### Scenario: Redis 业务 store 未接线
- **WHEN** production profile 将 session、knowledge、tool、audit 或 telemetry durable store 配置为 Redis，但没有对应 active implementation
- **THEN** 系统 SHALL 将该 durable store readiness 标记为失败
- **AND** 文档 SHALL 说明 Redis 当前不能作为这些业务记录的生产 store

### Requirement: Sandbox 和 workspace snapshot 持久化
系统 SHALL 为生产 sandbox 或远程 workspace 状态提供 snapshot store，支持保存、读取、列举和删除快照。当前可验收生产后端 SHALL 是 JDBC-backed snapshot store；OSS、S3 和 MinIO snapshot store SHALL 在真实实现、权限模型、健康检查和发布文档完成后作为后续扩展启用。

#### Scenario: 保存 sandbox 快照
- **WHEN** 生产环境中 sandbox 任务需要保存工作区状态
- **THEN** 系统 SHALL 将快照写入当前已接线的 snapshot store
- **AND** 快照 SHALL 记录租户、Agent、会话、任务标识、创建时间和后端位置

#### Scenario: 从快照恢复工作区
- **WHEN** sandbox 任务需要在另一个服务实例或后续执行中恢复
- **THEN** 系统 SHALL 能够从 snapshot store 读取对应快照
- **AND** 系统 SHALL 在权限校验通过后恢复到正确工作区范围

#### Scenario: 对象存储 snapshot 仍是扩展入口
- **WHEN** 文档或配置提到 OSS、S3 或 MinIO snapshot store
- **THEN** 文档 SHALL 说明这些后端需要后续真实实现和健康检查后才能用于生产验收

### Requirement: 生产 readiness 持久化校验
系统 SHALL 在启动期、健康检查和发布门禁中校验 durable persistence 能力，只有真实 store 可达、schema 就绪、active bean 类型正确、恢复测试通过时，相关 gate 才能通过。对象存储 bucket 就绪校验 SHALL 只适用于后续已接线的对象存储 snapshot 实现。

#### Scenario: Redis 配置存在但未接入实现
- **WHEN** production profile 配置了 Redis URI 但 active Agent state 仍使用本地 `JsonSession`
- **THEN** 系统 SHALL 将 distributed-state readiness 标记为失败
- **AND** 发布门禁 SHALL 报告具体失败原因

#### Scenario: 数据库 schema 缺失
- **WHEN** production profile 选择 MySQL/JDBC durable store 但必需 schema 或 migration 未初始化
- **THEN** 系统 SHALL 在启动期或 readiness 检查中失败
- **AND** 错误信息 SHALL 指出缺失的 store 或 schema 类别

#### Scenario: 未接线的对象存储后端
- **WHEN** production profile 配置 OSS、S3 或 MinIO snapshot 类型但 active snapshot implementation 不存在
- **THEN** 系统 SHALL 将 snapshot readiness 标记为失败
- **AND** 发布门禁 SHALL 说明该后端当前不是可验收生产实现

#### Scenario: 发布门禁基于真实能力状态
- **WHEN** 运营人员查看发布 readiness
- **THEN** 系统 SHALL 基于 durable store、snapshot store、telemetry、budget counter 和 AgentScope state 的实际健康状态计算 gate
- **AND** 系统 SHALL NOT 硬编码生产运行时 gate 为通过
