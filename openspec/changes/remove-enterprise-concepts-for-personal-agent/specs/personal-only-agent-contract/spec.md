## ADDED Requirements

### Requirement: 个人身份契约
系统 SHALL 使用 owner、agent、session 和 workspace 作为个人 Agent 的默认身份与状态边界。

#### Scenario: 创建个人运行时上下文
- **WHEN** 用户发起个人 Agent 请求并提供 owner、agent 和 session
- **THEN** 系统 SHALL 创建只包含 owner、agent、session 和 workspace 语义的运行时上下文
- **AND** 系统 MUST NOT 要求调用方提供企业租户、企业角色或企业部门

#### Scenario: 隔离个人状态
- **WHEN** 两个 owner 使用相同 agent 或 session 标识访问状态、记忆、工具授权或 trace
- **THEN** 系统 SHALL 按 owner 边界隔离数据
- **AND** 系统 MUST NOT 通过企业租户字段表达该隔离

### Requirement: 个人公共 API
系统 SHALL 将个人版公共 REST API、请求模型、响应模型和前端 API client 迁移为个人 Agent 契约。

#### Scenario: 发送个人聊天请求
- **WHEN** 客户端调用聊天 API
- **THEN** 请求 SHALL 使用 owner、agent、session、message 和个人能力开关表达意图
- **AND** 请求 MUST NOT 将 `tenantId`、`roles` 或 `departments` 作为必填或文档化字段

#### Scenario: 管理个人能力
- **WHEN** 客户端管理工作区文件、知识源、记忆、工具、技能、Agent 配置或 trace
- **THEN** API SHALL 使用 owner scope 和对应资源标识
- **AND** API MUST NOT 暴露企业 admin、ops、auditor 或 release gate 语义

### Requirement: 个人授权模型
系统 SHALL 用个人授权、工具确认、沙箱和最小审计替代企业 RBAC、部门 ACL 和企业审批流程。

#### Scenario: 执行低风险个人工具
- **WHEN** owner 执行已启用且无副作用的个人工具
- **THEN** 系统 SHALL 按个人工具授权策略允许执行
- **AND** 系统 MUST NOT 查询企业角色或企业部门权限

#### Scenario: 执行高风险个人工具
- **WHEN** owner 执行 Shell、代码、SQL、外部写操作、网络副作用或高风险工具
- **THEN** 系统 SHALL 要求个人确认或沙箱策略
- **AND** 系统 SHALL 记录脱敏的个人最小审计或 trace 事件

### Requirement: 个人工作台信息架构
系统 SHALL 将默认 Web UI 呈现为个人 Agent 工作台。

#### Scenario: 打开默认工作台
- **WHEN** 用户打开 Web 应用首页
- **THEN** 工作台 SHALL 展示聊天、计划、文件、知识/记忆、工具、技能、Agent 配置和 trace/diagnostics
- **AND** 工作台 MUST NOT 默认展示租户切换、企业角色、企业部门、管理后台、运维报表或发布门禁入口

#### Scenario: 查看个人身份
- **WHEN** 工作台展示当前身份或运行上下文
- **THEN** UI SHALL 展示 owner 和当前 Agent
- **AND** UI MUST NOT 要求用户理解企业租户、企业 RBAC 角色或企业部门

### Requirement: 个人版文档术语
系统 SHALL 在 README、docs、web README、OpenSpec artifacts 和发布说明中使用个人 Agent 术语描述默认产品。

#### Scenario: 阅读个人版使用文档
- **WHEN** 读者查看默认安装、启动、API、前端或发布文档
- **THEN** 文档 SHALL 描述 owner、personal Agent、workspace、memory、tools、skills 和 trace
- **AND** 文档 MUST NOT 将企业平台、企业助手、企业租户、企业 RBAC、运营后台或企业发布门禁描述为默认目标

#### Scenario: 保留历史说明
- **WHEN** 文档需要说明旧企业概念
- **THEN** 该内容 SHALL 位于 legacy、migration、archive 或 diagnostics 语境
- **AND** 文档 SHALL 明确这些概念不是个人版默认产品路径
