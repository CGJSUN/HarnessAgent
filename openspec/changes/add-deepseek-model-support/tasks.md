## 1. 配置接入

- [x] 1.1 在 `src/main/resources/application.yml` 新增 `deepseek` model provider，配置 `type: openai-compatible`、`model-name: deepseek-v4-flash`、`api-key-ref: env:DEEPSEEK_API_KEY`、`base-url: https://api.deepseek.com` 和 `endpoint-path: /chat/completions`。
- [x] 1.2 保持默认 Agent 继续使用 `echo`，避免本地启动强依赖 `DEEPSEEK_API_KEY`。
- [x] 1.3 在个人本地配置或文档示例中补充 `personal-assistant` 切换到 `deepseek`、覆盖 `model-name: deepseek-v4-pro` 和配置 `fallback-providers: [echo]` 的示例。
- [x] 1.4 确认源码、测试 fixture 和文档示例不包含真实 DeepSeek API key。

## 2. 运行时行为

- [x] 2.1 确认 `ModelConfigurationResolver` 解析 `deepseek` provider 时保留 provider id 为 `deepseek`，并将 provider type 解析为 `openai-compatible`。
- [x] 2.2 确认 `OpenAICompatibleModelProvider` 使用 provider id 读取 DeepSeek 的 `base-url`、`endpoint-path`、`model-name` 和 `api-key-ref`。
- [x] 2.3 确认缺少 `DEEPSEEK_API_KEY` 时只在创建 DeepSeek 模型实例时失败，不影响默认 `echo` provider 启动。
- [x] 2.4 确认 DeepSeek 模型调用进入现有非流式和流式聊天链路，不新增聊天 API 契约。
- [x] 2.5 确认 DeepSeek 可重试失败沿用现有 fallback 策略，配置错误、缺少密钥和预算拒绝不触发 fallback。

## 3. 测试覆盖

- [x] 3.1 更新或新增 `ModelConfigurationResolverTest`，覆盖 `deepseek` provider id、provider type、默认模型名、Agent 级模型覆盖和 `env:DEEPSEEK_API_KEY` 解析。
- [x] 3.2 更新或新增 `OpenAICompatibleModelProviderTest`，覆盖 DeepSeek base URL、endpoint path、模型名和密钥引用进入 OpenAI-compatible builder 路径。
- [x] 3.3 更新或新增模型 provider 失败测试，覆盖缺少 `DEEPSEEK_API_KEY` 时的明确错误。
- [x] 3.4 更新或新增 `ChatServiceTest`，覆盖使用 `deepseek` provider 的非流式聊天、流式聊天或 fallback 行为中至少一个端到端服务场景。
- [x] 3.5 如前端 Agent 配置视图涉及 provider 示例或默认值，补充对应 Vitest 或 Playwright 覆盖。

## 4. 文档与验证

- [x] 4.1 更新 `docs/start.md`，说明 DeepSeek provider 配置、推荐模型名、`DEEPSEEK_API_KEY` 和弃用模型名风险。
- [x] 4.2 更新 `docs/local-run-debugging.md`，补充本地切换到 DeepSeek 的最小调试步骤和回退到 `echo` 的方法。
- [x] 4.3 已确认 README 和 `web/README.md` 的本地运行说明不涉及模型 provider，无需补充 DeepSeek 链接或示例。
- [x] 4.4 运行 `openspec validate add-deepseek-model-support`。
- [x] 4.5 运行相关后端测试，至少覆盖 `ModelConfigurationResolverTest`、`OpenAICompatibleModelProviderTest` 和涉及 DeepSeek 的聊天测试。
- [x] 4.6 未修改前端代码或浏览器流程，无需运行 `cd web && npm run test:unit` 或 `cd web && npm run test:browser`。
- [x] 4.7 运行 `node scripts/validate-personal-terms.mjs`，确认新增文档不引入个人版禁用术语。
