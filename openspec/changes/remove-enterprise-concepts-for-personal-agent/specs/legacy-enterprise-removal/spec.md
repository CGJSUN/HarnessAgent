## ADDED Requirements

### Requirement: 企业入口隔离
系统 SHALL 将企业平台时代的 admin、ops、audit、release gate 和 enterprise-shaped endpoint 从个人版默认路径中移除或隔离。

#### Scenario: 默认调用个人入口
- **WHEN** 客户端使用默认 API 或默认 Web 工作台
- **THEN** 系统 SHALL 只暴露个人 Agent 能力入口
- **AND** 系统 MUST NOT 默认路由到企业管理、企业运维、企业审计报表或企业发布门禁

#### Scenario: 访问 legacy 入口
- **WHEN** 维护者启用并访问 legacy 或 diagnostics 入口
- **THEN** 系统 SHALL 明确标记该入口为迁移期能力
- **AND** 该入口 MUST NOT 被个人工作台默认调用或作为个人版验收依据

### Requirement: 企业身份字段下线
系统 SHALL 从个人版主代码路径中移除企业租户、企业角色和企业部门字段。

#### Scenario: 扫描个人主路径
- **WHEN** 运行企业术语门禁
- **THEN** 门禁 SHALL 检查 API request/response、service command、domain model、frontend types、默认文档和测试夹具
- **AND** 门禁 SHALL 在这些主路径出现未允许的 tenant、RBAC、role、department、admin、ops、release gate 或 enterprise console 术语时失败

#### Scenario: 允许迁移语境
- **WHEN** 企业术语出现在 migration、legacy adapter、archive、数据库迁移 SQL 或历史说明中
- **THEN** 门禁 SHALL 允许该术语
- **AND** 允许列表 SHALL 显式记录原因和范围

### Requirement: 持久化 owner 迁移
系统 SHALL 将持久化 schema、状态 key、预算 key、telemetry key、snapshot key 和本地状态文件迁移到 owner scope。

#### Scenario: 执行 schema migration
- **WHEN** 数据库中存在历史 `tenant_id` 和 `user_id` 数据
- **THEN** migration SHALL 将个人数据迁移为 owner、agent、session 和 resource 维度
- **AND** migration MUST preserve 会话、记忆、知识源、工具确认、幂等记录、trace、telemetry、snapshot 和 Agent state

#### Scenario: 读取旧状态 key
- **WHEN** 系统启动后发现旧 `tenant` 形态的本地或远端状态 key
- **THEN** 系统 SHALL 迁移或兼容读取一次并写入新的 owner key
- **AND** 后续写入 MUST 使用 owner key

### Requirement: 企业权限模型下线
系统 SHALL 删除默认企业 RBAC、部门 ACL、企业审批人和企业身份提供方依赖。

#### Scenario: RAG 检索个人知识
- **WHEN** owner 检索个人知识源或个人记忆
- **THEN** 系统 SHALL 使用 owner、agent、source visibility 和个人授权判断可见性
- **AND** 系统 MUST NOT 依赖企业部门、企业角色或企业用户列表

#### Scenario: 变更技能版本
- **WHEN** owner 启用、禁用、升级、回滚或锁定个人 Skill
- **THEN** 系统 SHALL 使用个人授权和本地审计记录变更
- **AND** 系统 MUST NOT 要求企业审批人或企业发布流程

### Requirement: 迁移回滚安全
系统 SHALL 为去企业化迁移提供备份、验证和回滚边界。

#### Scenario: 发布迁移
- **WHEN** 发布包含 schema、state key 或公共 API 破坏性变更
- **THEN** 发布文档 SHALL 要求先备份数据库、本地 state、workspace、snapshot 和工具确认记录
- **AND** 发布文档 SHALL 说明旧服务不能读取已完成 owner-scope 迁移的新 schema

#### Scenario: 回滚迁移
- **WHEN** 迁移后需要回滚
- **THEN** 系统 SHALL 使用备份恢复或明确的反向 migration
- **AND** 系统 MUST NOT 通过删除审计、记忆、工具确认、snapshot 或 Agent state 来完成回滚
