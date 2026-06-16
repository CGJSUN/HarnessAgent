## ADDED Requirements

### Requirement: 个人工具注册表
系统 SHALL 注册个人工具，并记录名称、描述、输入 schema、输出 schema、风险等级、权限策略和来源。

#### Scenario: 注册本地文件搜索工具
- **WHEN** 系统启动内置文件搜索工具
- **THEN** 工具注册表 SHALL 保存该工具的 schema、风险等级、来源和权限策略

### Requirement: 工具参数校验
系统 SHALL 在工具执行前基于 schema、白名单和上下文策略校验参数。

#### Scenario: 参数包含越权路径
- **WHEN** 工具调用参数包含未授权文件路径
- **THEN** 系统 SHALL 在执行前拒绝该调用
- **AND** 拒绝原因 SHALL 返回给 Agent 和 trace

### Requirement: 权限三态决策
系统 SHALL 对工具执行给出允许、需要确认或拒绝三态决策。

#### Scenario: 只读工具自动允许
- **WHEN** Agent 调用已授权的只读工具且参数合法
- **THEN** 系统 SHALL 允许工具执行

#### Scenario: 写操作需要确认
- **WHEN** Agent 调用会修改文件、发送请求或执行命令的工具
- **THEN** 系统 SHALL 暂停执行并要求用户确认

### Requirement: Human-in-the-loop 恢复
系统 SHALL 支持工具确认暂停点，并在用户确认、拒绝或修改参数后精确恢复执行。

#### Scenario: 用户确认工具
- **WHEN** 用户确认一个等待中的工具调用
- **THEN** 系统 SHALL 从暂停点继续执行该工具
- **AND** 后续事件 SHALL 关联同一个执行 trace

### Requirement: 工具结果结构化
系统 SHALL 将工具结果保存为结构化事件和消息内容块，并支持前端展示摘要、明细和原始引用。

#### Scenario: 展示工具结果
- **WHEN** 工具返回表格、文件或 JSON 结果
- **THEN** 系统 SHALL 保留结构化结果
- **AND** 前端 SHALL 能展示适合该结果类型的摘要

### Requirement: 幂等和最小审计
系统 SHALL 对有副作用的工具调用生成幂等标识，并记录脱敏后的最小审计信息。

#### Scenario: 重试写文件工具
- **WHEN** 写文件工具执行超时后被重试
- **THEN** 系统 SHALL 使用幂等标识避免重复写入或返回可确认的冲突状态

### Requirement: MCP 和协议工具来源
系统 SHALL 支持将已配置 MCP 或协议适配器作为工具来源，并应用同样的权限、确认和审计策略。

#### Scenario: 调用外部协议工具
- **WHEN** Agent 调用协议适配器提供的工具
- **THEN** 系统 SHALL 在外部调用前执行本地工具治理策略
