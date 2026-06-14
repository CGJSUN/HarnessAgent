## ADDED Requirements

### Requirement: HarnessAgent 运行时构建
系统 SHALL 通过 HarnessAgent 构建企业 Agent，并显式配置 name、system prompt、model、workspace 和 compaction。

#### Scenario: 创建第一个企业 Agent
- **WHEN** 平台使用有效的 Agent 配置启动
- **THEN** 系统 SHALL 为该 Agent 创建 HarnessAgent 实例
- **AND** HarnessAgent SHALL 使用配置中的 name、system prompt、model、workspace 和 compaction

### Requirement: 模型提供方抽象
系统 SHALL 通过统一的 ModelProvider 抽象访问模型提供方，业务代码不能直接绑定某一个供应商 SDK。

#### Scenario: 配置首个模型提供方
- **WHEN** 运维人员配置 DashScope、OpenAI、Anthropic、Gemini 或 Ollama 等模型提供方
- **THEN** 系统 SHALL 从配置或密钥存储加载供应商凭证
- **AND** 聊天执行 SHALL 通过 ModelProvider 抽象调用已配置供应商

### Requirement: 聊天 API
系统 SHALL 提供聊天 API，接收用户消息、Agent 标识、租户标识、用户标识和会话标识。

#### Scenario: 向 Agent 发送消息
- **WHEN** 用户向 `POST /api/chat` 提交消息
- **THEN** 系统 SHALL 调用目标 HarnessAgent
- **AND** 响应 SHALL 归属到传入的租户、用户、Agent 和会话

### Requirement: 会话和消息 API
系统 SHALL 提供会话列表、消息读取和会话删除 API。

#### Scenario: 管理会话历史
- **WHEN** 用户请求会话和消息历史
- **THEN** 系统 SHALL 只返回该租户、用户和 Agent 可见的会话与消息
- **AND** `DELETE /api/sessions/{id}` SHALL 按配置的留存策略删除或标记所选会话

### Requirement: RuntimeContext 隔离
系统 SHALL 在每次 Agent 调用中基于租户、用户、Agent 和会话标识派生运行时上下文 key。

#### Scenario: 两个用户使用同一个 Agent
- **WHEN** 两个用户使用不同租户或用户标识向同一个 Agent 发送消息
- **THEN** 系统 SHALL NOT 在他们之间共享会话状态、记忆、工作区数据或消息历史

### Requirement: 流式响应事件
系统 SHALL 支持流式响应，并输出文本增量、执行状态、存在时的工具事件、错误事件和完成事件。

#### Scenario: 接收流式回答
- **WHEN** 客户端请求流式聊天响应
- **THEN** 系统 SHALL 在模型产生文本时持续输出文本片段
- **AND** 流 SHALL 包含终止性的完成事件或错误事件

### Requirement: 开发期状态存储
MVP 运行时 SHALL 支持本地文件状态用于开发，同时保持状态存储接口可替换。

#### Scenario: 本地运行 MVP
- **WHEN** 平台运行在开发 profile
- **THEN** 系统 MAY 使用兼容 JsonFileAgentStateStore 的本地持久化
- **AND** 应用代码 SHALL NOT 依赖本地文件存储语义来定义生产行为
