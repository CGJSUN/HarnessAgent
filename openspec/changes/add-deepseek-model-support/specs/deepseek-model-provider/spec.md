## ADDED Requirements

### Requirement: DeepSeek provider 默认配置
系统 SHALL 提供名为 `deepseek` 的模型 provider 配置，并 SHALL 通过 `openai-compatible` provider 类型访问 DeepSeek OpenAI-compatible chat API。

#### Scenario: 加载 DeepSeek provider
- **WHEN** 应用加载默认模型 provider 配置
- **THEN** 系统 SHALL 存在 provider id 为 `deepseek` 的配置
- **AND** 该配置的 `type` SHALL 为 `openai-compatible`
- **AND** 该配置的 `base-url` SHALL 为 `https://api.deepseek.com`
- **AND** 该配置的 `endpoint-path` SHALL 为 `/chat/completions`

#### Scenario: 使用未弃用模型作为默认值
- **WHEN** 系统解析 `deepseek` provider 的默认模型名
- **THEN** 默认模型名 SHALL 使用未被 DeepSeek 官方标记为弃用的模型
- **AND** 默认模型名 SHALL NOT 使用 `deepseek-chat` 或 `deepseek-reasoner`

### Requirement: DeepSeek 密钥解析
系统 SHALL 使用外部密钥引用解析 DeepSeek API key，并 SHALL NOT 要求或允许示例配置包含真实密钥值。

#### Scenario: 从环境变量引用解析密钥
- **WHEN** Agent 选择 `deepseek` provider
- **THEN** 系统 SHALL 使用 `env:DEEPSEEK_API_KEY` 作为默认密钥引用
- **AND** 系统 SHALL 通过 `SecretStore` 解析该引用

#### Scenario: 缺少 DeepSeek 密钥
- **WHEN** Agent 选择 `deepseek` provider 且 `DEEPSEEK_API_KEY` 未配置
- **THEN** 系统 SHALL 拒绝创建 DeepSeek 模型实例
- **AND** 错误 SHALL 明确指出 OpenAI-compatible API key 未配置
- **AND** 本地默认 `echo` provider 的启动 SHALL NOT 受到影响

### Requirement: Agent 级 DeepSeek 模型选择
系统 SHALL 允许个人 Agent 通过现有 Agent 配置选择 `deepseek` provider，并 SHALL 支持 Agent 级模型名覆盖。

#### Scenario: Agent 切换到 DeepSeek
- **WHEN** 用户将某个 Agent 的 `model-provider` 配置为 `deepseek`
- **THEN** 后续该 Agent 的模型调用 SHALL 通过 `ModelProvider` 抽象路由到 OpenAI-compatible provider
- **AND** provider request 的 provider id SHALL 保留为 `deepseek`

#### Scenario: 覆盖 DeepSeek 模型名
- **WHEN** 用户为选择 `deepseek` provider 的 Agent 配置 `model-name`
- **THEN** 系统 SHALL 优先使用 Agent 级 `model-name`
- **AND** 未配置 Agent 级模型名时 SHALL 使用 `deepseek` provider 的默认模型名

### Requirement: DeepSeek 聊天链路
系统 SHALL 在不改变聊天 API 契约的情况下支持 DeepSeek 非流式和流式聊天。

#### Scenario: DeepSeek 非流式聊天
- **WHEN** 用户调用 `POST /api/chat` 且目标 Agent 使用 `deepseek` provider
- **THEN** 系统 SHALL 使用 DeepSeek provider 配置创建模型实例
- **AND** 响应 SHALL 沿用现有聊天响应结构

#### Scenario: DeepSeek 流式聊天
- **WHEN** 用户调用 `POST /api/chat/stream` 且目标 Agent 使用 `deepseek` provider
- **THEN** 系统 SHALL 通过现有流式事件链路输出模型事件
- **AND** 流式事件 SHALL 沿用现有事件结构和完成/错误语义

### Requirement: DeepSeek fallback
系统 SHALL 将 DeepSeek 模型失败纳入现有 fallback 策略，并 SHALL NOT 为 DeepSeek 创建独立失败处理分支。

#### Scenario: DeepSeek 可重试失败后 fallback
- **WHEN** DeepSeek 模型调用返回运行时配置中定义的可重试失败
- **AND** 该 Agent 配置了 fallback provider
- **THEN** 系统 SHALL 按现有 fallback 顺序尝试下一个 provider

#### Scenario: DeepSeek 权限或配置错误不 fallback
- **WHEN** DeepSeek 模型调用因缺少密钥、预算限制或非法配置被拒绝
- **THEN** 系统 SHALL 返回结构化失败原因
- **AND** 系统 SHALL NOT 将该拒绝误判为可重试模型失败

### Requirement: DeepSeek 文档和工作台配置
系统 SHALL 文档化 DeepSeek provider 的本地配置方式，并 SHALL 让工作台沿用现有 Agent 模型配置能力选择 DeepSeek。

#### Scenario: 文档展示 DeepSeek 最小配置
- **WHEN** 用户阅读本地运行或个人 Agent 使用文档
- **THEN** 文档 SHALL 包含 `DEEPSEEK_API_KEY`、`model-provider: deepseek`、`base-url` 和 `endpoint-path` 的示例
- **AND** 文档 SHALL 提示 `deepseek-chat` 与 `deepseek-reasoner` 的弃用风险

#### Scenario: 工作台保存 DeepSeek 配置
- **WHEN** 用户在工作台将 Agent 模型 provider 填为 `deepseek`
- **THEN** 工作台 SHALL 通过现有 Agent 配置接口保存该 provider 值
- **AND** 后续该 Agent 会话 SHALL 使用已保存的 DeepSeek 模型配置
