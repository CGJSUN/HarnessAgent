## Why

企业 Agent 平台已经暴露聊天、会话、RAG、工具、控制台、指标、成本、审计和编排 API，但还没有面向用户、管理员、运维和审计人员的浏览器 Web UI。需要增加一个专门的 Web 控制台，让已完成的后端能力能以企业工作台的形式被使用，而不是只通过 curl 或 API 客户端验证。

## What Changes

- 增加企业 Agent 工作台的浏览器 Web UI。
- 提供用户聊天控制台，覆盖会话导航、消息历史、流式回答、RAG 引用、无答案状态、工具状态和高风险工具确认。
- 提供管理员视图，覆盖后端 API 已支持的 Agent 配置、工具启停、知识源管理和 Skill 生命周期操作。
- 提供运营和审计视图，覆盖指标、成本报表、发布就绪、编排 trace 和审计搜索。
- 增加前端 API client 行为，覆盖可信身份头、本地开发身份模拟、后端错误响应、基于 POST 的流式处理和按角色导航。
- 增加开发期和生产期的 UI 集成方式，包括本地代理到 Spring Boot 后端，以及可选将构建产物交给 Spring 静态资源托管。
- 明确影响 UI 完整性的后端缺口，包括 CORS/代理策略、消息历史文档与实现不一致、部分管理动作缺少 console 聚合入口，以及用户控制台引用/上传聚合不完整。

## Capabilities

### New Capabilities

- `web-ui-console`: 基于浏览器的企业 Agent 工作台，覆盖用户聊天、管理、运营、审计、发布就绪和编排 trace 视图。

### Modified Capabilities

- 无。已完成的 `enterprise-agent-platform` 变更定义了后端控制台行为，但 `openspec/specs/` 下还没有已归档的主规格可修改。

## Impact

- 影响代码：新的前端工程或静态 UI 资源、前端 API client 模块、Web UI 测试，以及可选的 Spring Boot 静态资源/CORS/代理集成。
- 影响 API：现有 `/api/chat`、`/api/chat/stream`、`/api/sessions`、`/api/messages`、`/api/console/**`、`/api/knowledge/**`、`/api/tools/**`、`/api/orchestration/**` 和 `/api/release/**` 端点。
- 影响运行时：本地开发需要同源代理或 CORS 支持；生产环境应依赖网关或认证层注入可信身份头。
- 影响依赖：如果选择完整 Web app，预计需要 Node 前端工具链，并需要浏览器测试工具做视觉和工作流验证。
