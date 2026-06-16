## ADDED Requirements

### Requirement: 个人运行上下文
系统 SHALL 以个人 owner 和 workspace 为默认运行上下文，并在内部状态 key 中保留 owner、agent 和 session 维度。

#### Scenario: 创建个人会话
- **WHEN** 用户以默认个人 profile 发送第一条消息
- **THEN** 系统 SHALL 为该 owner、agent 和 session 创建独立上下文
- **AND** 系统 SHALL NOT 要求用户提供企业租户或企业角色信息

#### Scenario: 隔离两个个人 Agent
- **WHEN** 同一 owner 使用两个不同 agentId 进行会话
- **THEN** 系统 SHALL NOT 在两个 Agent 之间隐式共享会话状态、工具授权或工作区文件

### Requirement: 个人聊天 API
系统 SHALL 提供个人版非流式聊天 API，接收 agentId、sessionId、message 和可选上下文，并返回结构化回答。

#### Scenario: 发送非流式消息
- **WHEN** 用户调用个人聊天 API 发送消息
- **THEN** 系统 SHALL 调用目标 Agent 生成回答
- **AND** 响应 SHALL 包含消息标识、会话标识、文本内容和执行摘要

### Requirement: 类型化流式事件
系统 SHALL 提供类型化流式事件，覆盖文本增量、模型状态、工具调用、工具结果、子 Agent 事件、错误和完成事件。

#### Scenario: 接收流式回答
- **WHEN** 用户请求流式聊天
- **THEN** 系统 SHALL 按产生顺序输出类型化事件
- **AND** 流 SHALL 以完成事件或错误事件结束

#### Scenario: 工具事件进入前端
- **WHEN** Agent 在流式执行中调用工具
- **THEN** 系统 SHALL 输出工具开始、工具结果或工具等待确认事件

### Requirement: ContentBlock 消息模型
系统 SHALL 使用可表达文本、文件、图片、音视频、模型思考和工具结果的统一内容块模型保存消息。

#### Scenario: 保存带文件的用户消息
- **WHEN** 用户在消息中附加文件
- **THEN** 系统 SHALL 将文本和文件引用保存为同一消息下的内容块
- **AND** 系统 SHALL 在构造期拒绝非法 role 或非法内容块组合

### Requirement: 模型提供方配置
系统 SHALL 通过统一 ModelProvider 抽象调用模型，并支持按个人 Agent 配置 provider、模型、密钥引用、预算和 fallback。

#### Scenario: 切换模型提供方
- **WHEN** 用户将某个 Agent 的模型从默认 echo provider 切换到 OpenAI-compatible provider
- **THEN** 后续调用 SHALL 通过 ModelProvider 抽象访问新 provider
- **AND** 密钥 SHALL 从配置或安全存储引用读取

### Requirement: AgentState 会话恢复
系统 SHALL 持久化个人 AgentState，并支持应用重启后基于 owner、agent 和 session 恢复上下文。

#### Scenario: 应用重启后恢复会话
- **WHEN** 应用重启后用户继续同一个 sessionId
- **THEN** 系统 SHALL 恢复该会话的消息历史、AgentState 和待处理执行状态

### Requirement: 预算超时和取消
系统 SHALL 对模型调用、流式响应、工具调用和子 Agent 执行应用可配置预算、超时和取消策略。

#### Scenario: 模型调用超过预算
- **WHEN** 一次 Agent 执行预计超过个人预算
- **THEN** 系统 SHALL 停止继续调用模型或工具
- **AND** 响应 SHALL 返回预算受限的结构化原因
