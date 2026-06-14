## ADDED Requirements

### Requirement: 用户聊天控制台
系统 SHALL 提供用户端聊天体验，包括流式输出、消息历史、启用时的文件上传、引用来源、工具状态和确认提示。

#### Scenario: 用户确认高风险操作
- **WHEN** Agent 在聊天控制台中提出高风险工具操作
- **THEN** 用户 SHALL 在确认或拒绝前看到操作摘要和参数

### Requirement: Agent 管理
系统 SHALL 允许授权管理员配置 Agent、Prompt、模型提供方选择、工作区 profile、压缩策略和已启用能力。

#### Scenario: 管理员更新 Agent Prompt
- **WHEN** 管理员更新 Agent Prompt
- **THEN** 系统 SHALL 保存变更，并记录操作者、时间戳和受影响 Agent 元数据

### Requirement: 工具管理
系统 SHALL 允许授权管理员注册、启用、禁用、分类和授权企业工具。

#### Scenario: 禁用风险工具
- **WHEN** 管理员禁用某个工具
- **THEN** Agent SHALL NOT 执行该工具，直到授权管理员重新启用

### Requirement: 知识库管理
系统 SHALL 允许授权管理员或业务专家管理知识源、版本、权限、索引状态和删除。

#### Scenario: 查看索引状态
- **WHEN** 授权用户查看某个知识源
- **THEN** 控制台 SHALL 展示索引状态、版本、可见范围和最近同步结果

### Requirement: Skill 管理
系统 SHALL 允许授权管理员管理 Skill 仓库、版本、发布状态、审批、禁用和回滚。

#### Scenario: 回滚 Skill 版本
- **WHEN** 管理员回滚某个 Skill
- **THEN** 平台 SHALL 将选定的已批准版本设为活动版本，并记录回滚事件

### Requirement: 运营指标
系统 SHALL 提供会话、模型调用、工具调用、RAG 结果、失败、耗时、Token 成本和用户反馈的运营视图。

#### Scenario: 分析失败会话
- **WHEN** 运维人员按失败分类过滤会话
- **THEN** 控制台 SHALL 在权限范围内展示相关会话及关联的模型、RAG、工具和 API 元数据

### Requirement: 成本和用量报表
系统 SHALL 在配置价格数据后，报告 Token 用量、模型提供方用量、租户用量、Agent 用量和成本估算。

#### Scenario: 查看租户成本
- **WHEN** 运维人员查看租户用量报表
- **THEN** 报表 SHALL 按租户和 Agent 展示 Token 用量与模型提供方用量

### Requirement: 审计搜索
系统 SHALL 提供带权限控制的审计记录搜索，覆盖会话、配置变更、工具调用、审批和人工干预。

#### Scenario: 搜索工具审计历史
- **WHEN** 审计人员按会话或用户搜索工具执行记录
- **THEN** 系统 SHALL 在审计访问权限范围内返回匹配的审计记录
