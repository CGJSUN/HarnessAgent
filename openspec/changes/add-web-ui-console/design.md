## Context

当前仓库是运行在 8080 端口的 Maven/Spring Boot 后端。它已经通过 REST 和基于 POST 的流式端点暴露企业 Agent 平台核心能力，包括聊天、会话、RAG、工具、console 聚合、编排、发布就绪、指标、成本和审计。仓库目前没有前端工程、没有 Node 包元数据，也没有 Spring 静态资源目录。

已完成的 `enterprise-agent-platform` 变更定义了管理/运营控制台能力，但实际实现形态是后端服务和 API。第一版 Web UI 应该是面向工作的浏览器控制台，消费现有 API，让员工、业务专家、管理员、运维和审计人员可以直接使用平台能力。

重要后端约束：

- `POST /api/chat/stream` 通过 POST 请求返回 `text/event-stream`，原生浏览器 `EventSource` 不适用。
- 身份优先从可信 headers 解析；请求体/query 身份主要用于本地开发。
- 角色失败和非法状态失败可能返回 conflict 风格的 API 错误，而不是标准 `401` 或 `403`。
- 当前没有 CORS 配置，独立前端开发服务需要 dev proxy 或后端 CORS 支持。
- 部分后端存储和管理变更是内存态，UI 不能暗示这些数据已经具备生产级持久化。

## Goals / Non-Goals

**Goals:**

- 增加企业 Agent 工作台的浏览器 Web UI。
- 第一屏直接呈现运营型控制台，包括导航、当前聊天、会话历史、引用、工具确认和状态信息。
- 尽量复用现有后端 API，覆盖用户、管理员、运维、审计员、发布和编排工作流。
- 清晰处理流式聊天、RAG 无答案、高风险工具确认、API 错误和角色受限视图。
- 支持本地开发，同时不改变生产环境的可信身份假设。
- 保持与现有 Spring Boot 后端和 Maven 项目兼容。

**Non-Goals:**

- 不用前端检查替代后端 RBAC、租户隔离、工具治理、RAG 过滤或审计 enforcement。
- 不把宣传页作为主入口。
- 第一版 Web UI 不实现新的认证提供方。
- 不承诺内存态后端数据具备持久化。
- 除 UI 必须补齐的 API 缺口外，不重设计 Agent、RAG、工具、编排或生产运行时领域。

## Decisions

### Decision 1: 增加独立前端工程

使用顶层 `web/` 前端工程承载浏览器 UI，包含独立的包元数据、脚本、源码结构、测试和构建产物。

理由：

- 当前仓库没有前端约定，独立工程能把前端工具链与 Maven 后端关注点隔离。
- 完整 Web UI 需要组件状态、路由、API client、流式处理和浏览器测试；相比无构建静态页，这种结构更易维护。
- 后续仍可把构建产物复制到 `src/main/resources/static`，或通过网关托管。

备选方案：

- `src/main/resources/static` + 原生 HTML/CSS/JS：启动更简单，但面对流式聊天、管理工作流和审计视图时维护成本会快速上升。
- 服务端模板：会增加后端耦合，不符合当前 REST/SSE API 边界。

### Decision 2: 本地开发使用同源代理

前端开发服务器将 `/api/**` 代理到 `localhost:8080` 的 Spring Boot 后端。生产部署可以由 Spring Boot 托管构建产物，也可以将 UI 和 API 放到同源网关路径下。

理由：

- 避免为了本地开发增加过宽的 CORS 规则。
- 符合生产身份假设：可信 headers 应由基础设施注入，而不是任意浏览器 JavaScript 自行伪造。

备选方案：

- 增加全局后端 CORS：方便，但容易过度放开；如果已有代理能力则不是必要条件。
- 前端直接跨源调用后端：代码简单，但浏览器环境脆弱，也不贴合可信网关部署。

### Decision 3: 身份视为环境提供，本地提供模拟器

UI 支持本地开发身份面板，允许设置 tenant、user、roles 和 departments；生产行为假设这些值由可信网关或认证层提供。

理由：

- 后端当前支持本地开发请求身份，也支持生产可信 headers。
- UI 需要在本地演练 admin、ops 和 auditor 流程，而不引入完整 IdP。
- UI 不能让用户误以为生产环境中的角色通常可以自助编辑。

备选方案：

- 现在实现完整登录：范围过大，且当前后端认证原语不足。
- 固定单个管理员身份：更快，但无法测试租户、用户和角色边界。

### Decision 4: 使用 fetch stream 解析流式聊天

聊天视图调用 `POST /api/chat/stream`，用 `fetch` 读取 response body stream，解析 server-sent event frame，并把 typed events 追加到当前 assistant 消息。

理由：

- 原生 `EventSource` 无法发送 JSON POST body。
- 现有后端端点已经返回 typed runtime events。
- Fetch streaming 可以支持取消、超时展示和 terminal/error 状态处理。

备选方案：

- 只使用非流式 `POST /api/chat`：实现更容易，但不满足预期的聊天控制台体验。
- 增加新的 GET streaming endpoint：未来可做，但前端已能消费现有 POST stream 时不是必要条件。

### Decision 5: 后端 RBAC 是权限源头

UI 在已知角色 hints 时隐藏或禁用不适合当前角色的导航，但每次特权 API 调用仍必须依赖后端授权，并清晰展示后端拒绝。

理由：

- 前端角色检查不是安全控制。
- 后端已经是 admin、ops、auditor、knowledge、tool 和 audit 视图的授权源。
- 后端错误不一定使用标准认证状态码，因此前端需要基于消息的错误展示和恢复路径。

备选方案：

- 只在前端做门禁：不安全，也不符合企业治理。
- 对所有人展示全部导航：简单，但对非管理员用户噪音大且容易误导。

### Decision 6: 围绕现有 API 构建并显式跟踪缺口

Web UI 优先消费现有后端端点，只在 UI 工作流完整性必须要求时补充后端改动。

实施时需要处理的已知缺口：

- 文档写的是 `GET /api/sessions/{sessionId}/messages`，但代码实现是 `GET /api/messages`。
- 用户控制台聚合对象有 latest citations 和 uploads 字段，但当前返回空列表。
- Console API 暴露知识源 revoke，但没有明显的 console delete action。
- Console API 暴露 Skill approve、publish 和 disable，但没有 rollback。
- 工具注册和授权位于 `/api/tools`，console 聚合只提供列表和启停。
- `GET /api/orchestration/agents/{agentId}/tool` 看起来有副作用，不应作为被动 UI 数据加载接口使用。

## Risks / Trade-offs

- [前端与后端契约漂移] -> 增加 typed API client、fixture 驱动组件测试，以及可行时针对运行中后端的工作流测试。
- [本地身份模拟被误解为生产认证] -> 明确标注这是本地开发行为，并记录生产可信 header 部署方式。
- [流式解析缺陷导致聊天输出异常] -> 覆盖 terminal、error、text delta、取消和超时路径测试。
- [CORS 阻断本地开发] -> 使用 Vite dev proxy 或同源托管，避免直接跨源浏览器调用。
- [UI 暗示内存态数据已持久化] -> 在运行状态和文案中准确说明 runtime-only 配置变更。
- [管理操作受后端支持不完整影响] -> 对不支持动作使用明确禁用状态，或补充范围很窄的后端端点。
- [控制台范围过大导致交付变慢] -> 分阶段实现：先用户聊天，再管理视图，最后运营/审计/发布/编排视图。

## Migration Plan

1. 创建前端工程和路由外壳。
2. 实现共享 API client、错误模型、身份上下文和 dev proxy。
3. 交付用户聊天控制台，覆盖 sessions、messages、非流式 fallback、流式聊天、引用、无答案状态和工具确认状态。
4. 增加 Agent、工具、知识源和 Skill 的管理员视图。
5. 增加运营、审计、发布就绪和编排 trace 视图。
6. 只在 UI 工作流完整性必须要求时补齐后端 API 缺口。
7. 增加浏览器级验证，并记录本地/生产启动路径。

回滚策略：

- 停止或禁用 Web UI 托管，不改变后端 Agent 行为。
- 保留现有 API 形式的管理能力。
- 如果为 UI 完整性新增后端端点，应保持向后兼容，或复用现有角色控制。

## Open Questions

- 生产环境应由 Spring Boot 静态资源托管构建后的 UI，还是使用独立前端主机，或网关控制路径？
- 实施时最终选择哪套前端栈：React/Vite/TypeScript、其他框架，还是无构建静态 baseline？
- 第一版是否包含完整 Skill rollback 和知识删除端点，还是先展示为未来动作？
- 本地身份模拟只在开发构建中暴露，还是 demo 构建也暴露？
- 如果未来多个企业应用复用组件，应采用哪套视觉设计系统？
