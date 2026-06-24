## Context

当前模型链路已经具备配置型扩展点：

- `harness-agent.model-providers` 支持按 provider id 配置 `type`、`model-name`、`api-key-ref`、`base-url` 和 `endpoint-path`。
- `ModelConfigurationResolver` 会解析 Agent 级 `model-provider`、`model-name`、`model-api-key-ref` 和 fallback provider。
- `ModelProviderRegistry` 会优先按 `provider.type` 找到真正执行的 `ModelProvider`，因此多个 provider id 可以复用同一个 `openai-compatible` 实现。
- `OpenAICompatibleModelProvider` 已能按 provider id 读取对应配置，并把 `base-url`、`endpoint-path`、模型名和密钥传给 AgentScope 的 `OpenAIChatModel`。

DeepSeek 官方文档显示，截至 2026-06-24，OpenAI-compatible `base_url` 为 `https://api.deepseek.com`，对话接口为 `/chat/completions`，推荐模型包括 `deepseek-v4-flash` 和 `deepseek-v4-pro`；`deepseek-chat` 与 `deepseek-reasoner` 将于北京时间 2026/07/24 23:59 弃用。该信息来自 DeepSeek 官方 API 文档：https://api-docs.deepseek.com/zh-cn/

## Goals / Non-Goals

**Goals:**

- 提供名为 `deepseek` 的模型 provider 配置，让个人 Agent 可以直接选择 DeepSeek。
- 默认通过 `openai-compatible` provider 类型接入 DeepSeek，不引入新 SDK。
- 使用 `DEEPSEEK_API_KEY` 作为默认密钥引用，并继续通过 `SecretStore` 解析。
- 默认模型使用未标记弃用的 DeepSeek 模型名，并允许 Agent 级配置覆盖模型名。
- 保持现有 `POST /api/chat` 和 `POST /api/chat/stream` 契约不变。
- 覆盖 provider 解析、OpenAI-compatible 请求配置、缺少密钥失败、fallback 和文档示例。

**Non-Goals:**

- 不新增 DeepSeek 专属 Java SDK 或专属 `DeepSeekModelProvider`，除非实现阶段发现 AgentScope `OpenAIChatModel` 无法满足基础对话。
- 不在本变更中实现 DeepSeek 专属 `thinking`、`reasoning_effort` 或其它 provider-specific extra body 配置；这些需要先扩展统一模型请求元数据。
- 不改变聊天请求/响应结构，不要求前端新增专门的 DeepSeek 页面。
- 不把 API key 写入源码、日志、测试快照或文档示例中的真实值。

## Decisions

### 1. 以配置型 provider 接入 DeepSeek

新增配置示例：

```yaml
harness-agent:
  model-providers:
    deepseek:
      type: openai-compatible
      model-name: deepseek-v4-flash
      api-key-ref: env:DEEPSEEK_API_KEY
      base-url: https://api.deepseek.com
      endpoint-path: /chat/completions
```

Agent 选择方式沿用现有配置：

```yaml
harness-agent:
  agents:
    personal-assistant:
      model-provider: deepseek
      model-name: deepseek-v4-pro
      fallback-providers: [echo]
```

理由：现有 `OpenAICompatibleModelProvider` 已经支持按 provider id 加载 base URL、endpoint path 和密钥引用；DeepSeek 官方也明确支持 OpenAI-compatible API 格式。这样可以把变更限制在配置、测试和文档层面，并复用已有预算、fallback、流式和非流式聊天链路。

备选方案是新增 `DeepSeekModelProvider`。该方案可以更早暴露 DeepSeek 专属参数，但会重复 OpenAI-compatible 请求构造，并扩大测试面。当前不采用，只有在基础对话无法通过 `OpenAIChatModel` 正常工作时再回退到该方案。

### 2. 默认模型选择 `deepseek-v4-flash`

默认 provider model name 使用 `deepseek-v4-flash`，文档说明可切换到 `deepseek-v4-pro`。不把 `deepseek-chat` 或 `deepseek-reasoner` 作为默认值。

理由：官方已标注 `deepseek-chat` 和 `deepseek-reasoner` 将在北京时间 2026/07/24 23:59 弃用；把它们作为默认值会让新配置很快过期。`deepseek-v4-flash` 更适合作为个人 Agent 的默认本地开发选择，`deepseek-v4-pro` 作为更强模型由 Agent 级配置覆盖。

### 3. 密钥只通过引用解析

默认配置使用 `api-key-ref: env:DEEPSEEK_API_KEY`。实现和测试只验证引用解析，不允许在配置、日志、测试 fixture 或文档中出现真实 key。

理由：这与当前 DashScope、OpenAI-compatible provider 的安全模式一致，也能让本地和生产环境用同一套 SecretStore 规则。

### 4. 前端工作台不新增专用 API

当前 Agent 配置视图的 model provider 是文本输入，后端 `PATCH /api/console/agents/{agentId}/config` 已接受 `modelProvider` 和 `modelName`。本变更只需要保证 DeepSeek provider 在后端配置中存在，并在文档中给出可填写值。

如果后续前端改成下拉选项，再通过已有 console view 或配置元数据补充 provider 列表；本变更不新增公共 API。

### 5. fallback 沿用现有策略

DeepSeek 失败时的 fallback 仍由 Agent 的 `fallback-providers` 和生产运行时 retryable status code 控制。建议示例使用 `[echo]` 作为本地可验证 fallback，不为 DeepSeek 增加特殊 fallback 分支。

理由：模型失败处理已经在 `ChatService` 和运行时配置中集中治理；DeepSeek 应进入同一链路，避免 provider-specific 分叉。

## Risks / Trade-offs

- [DeepSeek 官方模型名继续变化] → 默认值和文档示例要集中在配置和文档中，测试只断言当前支持的默认配置，不把弃用别名写成推荐路径。
- [AgentScope `OpenAIChatModel` 不支持 DeepSeek 某些专属参数] → 本变更先覆盖基础聊天；专属思考参数作为后续模型请求元数据扩展，不阻塞基础 provider 支持。
- [缺少 `DEEPSEEK_API_KEY` 导致启动后运行时报错] → provider 创建时保留明确错误信息；本地默认 Agent 仍使用 `echo`，只有显式切换到 DeepSeek 才需要 key。
- [DeepSeek 网络或限流失败影响聊天] → 通过既有 timeout、retryable status code 和 Agent fallback provider 兜底。
- [文档示例和真实环境不一致] → 增加配置绑定/解析测试，并在运行调试文档中给出最小 smoke 步骤。

## Migration Plan

1. 在默认配置中新增 `deepseek` provider，但不把默认 Agent 切到 DeepSeek，避免本地启动强依赖外部 key。
2. 在 `application-personal.yml` 或文档示例中说明如何把 `personal-assistant` 切到 `deepseek`。
3. 补充后端测试，验证 `deepseek` provider id 解析为 `openai-compatible` 类型，并使用 `env:DEEPSEEK_API_KEY`。
4. 补充 OpenAI-compatible provider 测试，验证 DeepSeek base URL、endpoint path 和模型名会进入 builder 配置路径。
5. 更新 `docs/start.md`、`docs/local-run-debugging.md` 和必要的 README 片段。
6. 回滚时删除或注释 `deepseek` provider 配置，并把任何 Agent 的 `model-provider` 切回 `echo` 或其它已配置 provider；无需数据迁移。

## Open Questions

- 是否要在首版直接提供 `deepseek-v4-pro` 的示例 Agent，还是只在文档中说明可选配置？
- 如果 AgentScope `OpenAIChatModel` 暂不支持 DeepSeek 的 `thinking` 参数，是否需要单独开一个后续变更扩展 provider-specific extra body？
