## Context

平台已有三个完成状态的 OpenSpec 变更：`enterprise-agent-platform`、`add-web-ui-console` 和 `add-durable-persistence`。后续复查发现，当前主要问题不是实现任务未完成，而是 MVP 支持行为、已配置但未实现的扩展入口、生产接线要求之间的边界容易被误读。

风险模式是：读者看到一个 enum、服务方法、配置 key 或通用 API 后，可能推断完整能力已经生产可用。例如 `MCP` 工具来源类型、OSS/S3/MinIO snapshot store 枚举、`SkillGovernanceService.rollback`、控制台知识删除服务方法、Redis durable store 配置项。文档需要用一致的契约区分这些入口和当前已开放工作流。

这个变更只做文档和规格治理，不修改运行时行为、数据库 schema、公共 API 语义、React 工作流或生产配置默认值。

## Goals / Non-Goals

**Goals:**

- 建立单一 OpenSpec capability，用于描述 MVP 边界治理。
- 为历史 completed change specs 中过宽的 SHALL 要求补充 delta，避免交接时把未来扩展误认为当前可验收范围。
- 让 RAG、工具/MCP、控制台管理、durable persistence、snapshot storage 和 production readiness 的 supported/not-supported 语言保持一致。
- 保留既有 MVP 变更的完成状态，同时明确它们的后续扩展边界。
- 定义未来如果要加入真实向量检索、外部 MCP 执行、对象存储 snapshot、Redis 业务 store 或更多控制台管理动作时，应如何另起 OpenSpec 变更。

**Non-Goals:**

- 不实现 embedding 生成、向量索引、向量数据库 adapter 或 reranking。
- 不实现真实 MCP client、外部系统工具 adapter 或新的执行传输层。
- 不新增知识删除、Skill rollback、完整工具注册/授权编辑的 console REST/UI。
- 不实现 OSS、S3 或 MinIO snapshot store。
- 不实现 Redis-backed session、knowledge、tool、audit 或 telemetry store。
- 不改变 release gate、schema migration、API contract 或前端行为，除文档对齐外不做运行时调整。

## Decisions

### Decision 1: 将本变更定位为边界治理能力

新增 `mvp-boundary-governance` capability，而不是直接回改历史完成变更。已完成变更继续作为已交付 MVP 范围的证据；本变更记录后续如何解释这些边界。

替代方案：重新打开每个已完成变更并修改它们的 proposal/design/tasks。这个做法会模糊“complete”的含义，也让团队难以区分当前是在修正文档，还是在扩大产品范围。

### Decision 1.1: 对历史宽泛规格增加 delta 解释

虽然当前仓库尚未将历史 change specs 归档到 `openspec/specs/`，这些 completed specs 仍会在交接、验收和后续规划时被阅读。凡是历史 requirement 的 `SHALL` 容易被理解为当前已经完整生产可用，本变更 SHALL 通过同名 capability 的 delta spec 收窄解释。

本次补充的 delta 覆盖：

- `admin-ops-console`：控制台工具注册/授权、知识删除、Skill rollback。
- `knowledge-rag`：Embedding、向量检索和重排序。
- `governed-tool-calling`：真实 MCP Server/client 调用。
- `production-runtime`：Redis/MySQL distributed state 和 OSS/S3/MinIO snapshot。
- `durable-persistence`：Redis 业务 store、对象存储 snapshot 和 readiness bucket 语义。

替代方案：只新增 `mvp-boundary-governance` 元能力。这个做法能通过 OpenSpec 校验，但无法直接收窄历史 specs 中已有的强要求，交接风险仍然存在。

### Decision 2: 使用“当前支持 / 当前不支持 / 后续扩展”三段式语言

当能力存在部分实现时，文档不应简单写成“未实现”。每个边界都应先说明当前支持的行为，再说明当前不包含什么，以及需要另起变更才能加入什么。

示例：

- RAG 支持受治理的词法检索、引用、权限过滤和无答案行为；真实 embedding/vector DB 检索是后续范围。
- MCP 支持作为受治理工具来源注册；真实外部 MCP server 执行是后续范围。
- 控制台支持列表、启停、撤销、审批、发布和禁用；删除、rollback 和完整工具编辑是后续范围。
- JDBC snapshot 是当前生产可验收后端；OSS/S3/MinIO snapshot 是后续范围。

替代方案：只在 `docs/continue.md` 中列出缺口。这个做法适合当前交接，但无法形成持久的 OpenSpec 契约，也无法约束未来文档和实现变更。

### Decision 3: 生产 ready 以真实接线和健康检查为准

边界契约必须说明：生产 readiness 取决于 active implementation 和 health check，而不只是配置项或 enum 是否存在。这一点影响 Redis 业务 store、对象存储 snapshot、telemetry 和默认 development persistence 的理解。

替代方案：把所有配置项都写成可用选项。这个做法会夸大当前生产面，因为部分选项只是占位或扩展入口，不是已接线的生产实现。

### Decision 4: 后续扩展必须另起 OpenSpec 变更

如果团队决定纳入任何延后能力，应为该能力创建独立 OpenSpec 变更，并包含自己的 proposal、specs、design 和 tasks。候选变更包括：

- `add-vector-rag-provider`
- `add-external-mcp-executor`
- `add-console-management-actions`
- `add-object-snapshot-stores`
- `add-redis-business-stores`

替代方案：把这些能力作为本变更的可选任务。这样会把边界澄清变成大范围实现工作，导致验收标准不清晰。

## Risks / Trade-offs

- [Risk] 未来实现变更落地后，文档边界再次漂移。-> Mitigation: 任何跨越当前边界的未来变更，都必须更新 `mvp-boundary-governance` 或归档后的对应 spec。
- [Risk] 把某些能力标成“后续范围”可能看起来像从既有宽泛文档中回退。-> Mitigation: 每处都先描述当前已支持的 MVP 行为，再描述排除的扩展能力。
- [Risk] 文档型变更被误认为已经完成生产验证。-> Mitigation: 发布文档必须继续强调 production profile、真实环境变量和 phase-gates 验证。
- [Risk] 多份文档维护边界文字会造成重复。-> Mitigation: `docs/continue.md` 保留详细交接清单，`docs/start.md`、`docs/release-readiness.md` 和 `web/README.md` 保持简洁一致。

## Migration Plan

1. 保持现有运行时和 UI 行为不变。
2. 新增 `mvp-boundary-governance` spec，定义可持久化验证的边界要求。
3. 将面向运维、前端和交接的文档对齐到 spec 语言。
4. specs 和 tasks 创建后运行 OpenSpec 校验。
5. 未来能力获批时，创建独立 OpenSpec 变更，不在本变更中悄悄扩范围。

回滚策略是文档级回滚：如果团队决定不正式固化 MVP 边界，撤回本变更的 OpenSpec artifacts 和相关文档措辞即可。

## Open Questions

- `mvp-boundary-governance` 是否在文档对齐后立即归档到 main specs，还是等发布评审确认后再归档？
- 后续实现变更是直接更新这个边界 spec，还是在归档后为每个具体能力维护更细粒度的 spec？
