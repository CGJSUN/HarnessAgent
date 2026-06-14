## ADDED Requirements

### Requirement: 企业工具注册表
系统 SHALL 注册企业工具，并记录名称、描述、归属系统、风险等级、输入 schema、权限策略和审计策略。

#### Scenario: 注册 CRM 查询工具
- **WHEN** 管理员注册 CRM 查询工具
- **THEN** 工具注册表 SHALL 保存该工具的 schema、风险等级、权限策略、归属系统和审计策略

### Requirement: 工具风险分级
系统 SHALL 至少将工具分为只读工具和高风险工具。

#### Scenario: 识别数据库更新工具
- **WHEN** 工具可以修改业务数据、发送消息、提交审批、执行代码或创建记录
- **THEN** 系统 SHALL 将该工具归类为高风险工具，除非管理员显式配置了更严格类别

### Requirement: 工具权限执行
系统 SHALL 在执行工具前检查用户、Agent、租户和工具权限。

#### Scenario: 用户缺少工具权限
- **WHEN** Agent 尝试为无权限用户调用工具
- **THEN** 系统 SHALL 拒绝执行
- **AND** 拒绝结果 SHALL 被记录到审计中

### Requirement: 结构化参数校验
系统 SHALL 在执行工具前根据结构化 schema 和已批准的参数约束校验工具输入。

#### Scenario: Prompt Injection 尝试注入危险参数
- **WHEN** 工具调用包含注册 schema 或白名单之外的参数
- **THEN** 系统 SHALL 在联系企业系统前拒绝该工具调用

### Requirement: 高风险工具人工确认
系统 SHALL 在执行高风险工具前要求用户显式确认或审核人审批。

#### Scenario: 提交审批申请
- **WHEN** Agent 提议提交审批申请
- **THEN** 系统 SHALL 展示操作摘要和参数以供确认或审批
- **AND** 系统 SHALL 只在审批通过后执行工具

### Requirement: 工具幂等和失败处理
对会修改外部系统的工具，系统 SHALL 支持幂等 key 和显式失败处理。

#### Scenario: 超时后重试写工具
- **WHEN** 高风险工具提交后发生超时
- **THEN** 系统 SHALL 在重试变更前使用幂等 key 或外部状态检查

### Requirement: 工具审计记录
系统 SHALL 记录工具调用的用户、租户、Agent、会话、工具名称、脱敏输入、脱敏输出、耗时、状态以及适用时的审批人。

#### Scenario: 审计成功工具执行
- **WHEN** 任意企业工具执行完成
- **THEN** 系统 SHALL 持久化包含执行元数据和脱敏结果的审计记录

### Requirement: MCP 工具接入
系统 SHALL 支持将已批准 MCP Server 作为工具来源接入，并应用同样的权限、校验、确认和审计控制。

#### Scenario: 调用 MCP 工具
- **WHEN** Agent 调用 MCP Server 提供的工具
- **THEN** 系统 SHALL 在 MCP 调用前后执行平台工具治理策略
