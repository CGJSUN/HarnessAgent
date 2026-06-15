## ADDED Requirements

### Requirement: MVP 边界声明
系统 SHALL 在 OpenSpec 和面向交接、运维、前端的文档中明确区分当前 MVP 支持能力、当前不支持能力和后续扩展点。

#### Scenario: 阅读完成状态的变更
- **WHEN** 读者查看已完成的 MVP 变更或发布文档
- **THEN** 文档 SHALL 明确说明完成状态代表当前规格内任务闭环
- **AND** 文档 SHALL NOT 暗示所有枚举、配置项、服务方法或通用 API 都已经完整生产可用

#### Scenario: 能力只有部分实现
- **WHEN** 某个能力存在元数据、枚举、服务方法或占位配置，但没有完整生产实现
- **THEN** 文档 SHALL 先说明当前支持的行为
- **AND** 文档 SHALL 明确列出当前不支持的扩展能力

#### Scenario: 历史 completed specs 存在更强表述
- **WHEN** 历史 completed change specs 中的 `SHALL` 要求容易被理解为当前完整生产可用
- **THEN** 本变更 SHALL 通过同名 capability 的 delta spec 收窄解释
- **AND** 文档 SHALL 说明收窄后的当前可验收边界

### Requirement: RAG 检索边界
系统 SHALL 将当前 RAG 能力描述为受权限治理的轻量词法检索，而不是 embedding-backed vector database 检索。

#### Scenario: 描述当前 RAG 检索
- **WHEN** 文档说明当前 RAG 检索能力
- **THEN** 文档 SHALL 说明当前 `vectorScore` 是基于 token overlap/Jaccard 风格的轻量评分
- **AND** 文档 SHALL 说明真实 embedding、向量索引、向量数据库 adapter 和 reranking 不属于当前 MVP

#### Scenario: 未来加入真实向量检索
- **WHEN** 团队决定加入 embedding-backed vector retrieval
- **THEN** 团队 SHALL 创建独立 OpenSpec 变更
- **AND** 该变更 SHALL 覆盖 embedding provider、索引构建、权限过滤、召回策略和重建流程

### Requirement: MCP 工具执行边界
系统 SHALL 将当前 MCP 能力描述为受治理工具来源，而不是已接入真实外部 MCP client 的执行能力。

#### Scenario: 描述当前 MCP 工具能力
- **WHEN** 文档说明 MCP 工具支持
- **THEN** 文档 SHALL 说明当前 MCP 工具 SHALL 走工具注册、权限、参数校验、确认、幂等和审计链路
- **AND** 文档 SHALL 说明真实 MCP server 调用、连接配置、超时重试、错误映射和结果脱敏适配不属于当前 MVP

#### Scenario: 未来加入外部 MCP 执行
- **WHEN** 团队决定接入真实 MCP client 或外部系统执行 adapter
- **THEN** 团队 SHALL 创建独立 OpenSpec 变更
- **AND** 该变更 SHALL 定义执行语义、失败处理、幂等重放、超时策略和审计字段

### Requirement: 控制台管理边界
系统 SHALL 明确控制台当前只开放轻量管理入口，并区分底层服务或通用 API 与 console REST/UI 的可用性。

#### Scenario: 描述知识源管理
- **WHEN** 文档说明控制台知识源管理
- **THEN** 文档 SHALL 说明 console 支持知识源列表和 revoke
- **AND** 文档 SHALL 说明 console 专用 delete REST/UI 当前未开放，即使通用知识删除 API 或服务方法存在

#### Scenario: 描述 Skill 管理
- **WHEN** 文档说明控制台 Skill 管理
- **THEN** 文档 SHALL 说明 console 支持 list、approve、publish 和 disable
- **AND** 文档 SHALL 说明 Skill rollback 当前只保留为发布预案或服务能力，未开放可执行 REST/UI

#### Scenario: 描述工具管理
- **WHEN** 文档说明控制台工具管理
- **THEN** 文档 SHALL 说明 console 当前支持已注册工具的列表和启停
- **AND** 文档 SHALL 说明完整工具注册、参数 schema 编辑和授权策略编辑仍由后续变更决策

### Requirement: 持久化和 Redis 边界
系统 SHALL 明确生产业务持久化当前以 MySQL/JDBC 为主，Redis 当前只作为部分共享状态的可选实现。

#### Scenario: 描述生产 durable stores
- **WHEN** 文档说明 production profile 或 durable persistence
- **THEN** 文档 SHALL 说明 session、knowledge、tool、audit 和 telemetry 的生产持久化路径当前是 MySQL/JDBC
- **AND** 文档 SHALL 说明 Redis 当前只覆盖 AgentScope state 和 budget counter

#### Scenario: Redis 业务 store 未接线
- **WHEN** 文档说明或示例涉及将 session、knowledge、tool、audit 或 telemetry 配置为 Redis
- **THEN** 文档 SHALL 说明这些 Redis 业务 store 当前不是生产可验收能力
- **AND** production readiness SHALL NOT 被描述为可仅凭这些配置通过

#### Scenario: 描述本地开发态
- **WHEN** 文档说明 development 或 test profile
- **THEN** 文档 SHALL 说明 H2、local-json 或 in-memory store 只适合本地调试和单进程测试
- **AND** 文档 SHALL NOT 将默认开发态描述为 production readiness 已通过

### Requirement: Snapshot 后端边界
系统 SHALL 明确当前可验收的生产 snapshot 后端是 JDBC，OSS/S3/MinIO 只是后续对象存储扩展入口。

#### Scenario: 描述生产 snapshot 配置
- **WHEN** 文档提供当前可验收的 production snapshot 示例
- **THEN** 文档 SHALL 使用 JDBC snapshot 配置作为当前生产验收路径
- **AND** 文档 SHALL 说明 OSS、S3 和 MinIO 需要真实实现和健康检查后才能声明生产可用

#### Scenario: 配置未接线的对象存储 snapshot
- **WHEN** 运维人员只配置 OSS、S3 或 MinIO 枚举值但没有对应生产实现
- **THEN** 文档 SHALL 说明该配置不能被视为 production ready

### Requirement: 生产 readiness 边界
系统 SHALL 将 production readiness 绑定到真实 profile、环境变量、active implementation 和健康检查结果，而不是绑定到文档示例或配置项存在。

#### Scenario: 发布前检查
- **WHEN** 团队执行发布验收
- **THEN** 文档 SHALL 要求使用 production profile 和真实环境变量运行 phase-gates
- **AND** `Production Runtime` gate SHALL 以 active implementation、schema、telemetry、budget 和 snapshot 健康状态为准

#### Scenario: 静态扫描完成
- **WHEN** 文档记录静态扫描或任务表完成
- **THEN** 文档 SHALL 说明静态扫描不能替代完整测试和生产接线验证

### Requirement: 未来扩展变更门禁
系统 SHALL 要求任何跨越当前 MVP 边界的能力通过独立 OpenSpec 变更进入实现。

#### Scenario: 提议加入后续能力
- **WHEN** 团队提议加入真实向量检索、外部 MCP 执行、对象存储 snapshot、Redis 业务 store、console delete、Skill rollback 或完整工具管理 UI
- **THEN** 团队 SHALL 创建新的 OpenSpec change
- **AND** 新变更 SHALL 包含 proposal、specs、design 和 tasks

#### Scenario: 更新既有边界
- **WHEN** 后续变更让某个未来扩展点变成生产可用能力
- **THEN** 团队 SHALL 更新 `mvp-boundary-governance` 或归档后的对应 spec
- **AND** 文档 SHALL 移除与新事实冲突的旧边界说明

### Requirement: 文档矩阵验收
系统 SHALL 使用可检查的文档矩阵确认关键 MVP 边界已经落到对应文档，而不是只在 OpenSpec 中抽象描述。

#### Scenario: 检查边界矩阵
- **WHEN** 团队完成本变更的文档对齐
- **THEN** tasks SHALL 覆盖 RAG、MCP、控制台知识删除、Skill rollback、工具注册授权 UI、snapshot、Redis 业务 store 和本地开发态与 production ready 的区别
- **AND** 每个任务 SHALL 指向至少一个需要检查或更新的文档位置
