## Why

HarnessAgent 已支持可配置的模型 provider，但 DeepSeek 还不是个人 Agent 的显式模型选项。想使用 DeepSeek 的用户目前只能手动拼通用 OpenAI-compatible 配置，缺少默认端点、模型名、fallback 行为和验收覆盖。

## What Changes

- 将 DeepSeek 加为个人 Agent 配置中的一等模型 provider 选项。
- 优先复用现有 OpenAI-compatible provider 路径，因为 DeepSeek 官方 API 支持 OpenAI-compatible 调用方式。
- 为 DeepSeek 定义安全默认值，包括 endpoint、endpoint path、模型名和 API key 引用，所有密钥仍必须来自外部环境或配置占位符。
- 通过现有 `ModelProvider` 抽象支持 DeepSeek 的非流式和流式聊天。
- 文档化 DeepSeek 模型名选择，默认优先使用 `deepseek-v4-flash` 或 `deepseek-v4-pro`，并提示 `deepseek-chat`、`deepseek-reasoner` 将于北京时间 2026/07/24 23:59 弃用。
- 增加聚焦测试，覆盖 provider 解析、请求路由、API key 引用、fallback 行为和本地文档示例。
- 不引入破坏性 API 变更。

## Capabilities

### New Capabilities

- `deepseek-model-provider`：DeepSeek 模型 provider 的配置和运行时行为，包括 OpenAI-compatible endpoint 默认值、Agent 级模型选择、流式支持、fallback 和密钥解析。

### Modified Capabilities

- 无。当前 `openspec/specs/` 下没有需要为本变更追加 delta 的主规格。

## Impact

- 后端配置：`src/main/resources/application.yml`、`application-personal.yml` 以及相关配置属性测试。
- 模型运行时：现有 provider 解析、OpenAI-compatible 请求构造和聊天 fallback 链路。
- API 行为：现有聊天接口继续沿用当前请求/响应契约，只是在配置中允许选择 `deepseek` provider。
- 前端工作台：Agent 模型配置应能在 DeepSeek 已配置时展示或选择该 provider。
- 文档：本地运行文档和个人 Agent 使用文档应说明 `DEEPSEEK_API_KEY` 与 DeepSeek provider 示例。
- 外部依赖：调用 DeepSeek compatible chat API；除非 design 阶段证明必要，否则不新增 SDK 依赖。
