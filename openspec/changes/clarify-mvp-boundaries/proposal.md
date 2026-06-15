## Why

当前 MVP 的实现任务已经完成，但部分枚举、服务方法、通用 API 或配置项容易让读者误以为相关能力已经完整生产可用。这个变更用于正式固化 MVP 支持边界，让发布、控制台、RAG、工具治理和持久化文档都能清楚区分“当前可验收能力”和“后续扩展点”。

## What Changes

- 定义一个正式的 MVP 边界治理契约，用于区分已完成的平台能力和未来集成工作。
- 明确 RAG 当前使用受权限治理的词法检索，`vectorScore` 是基于 token overlap/Jaccard 风格的轻量评分，不是 embedding-backed vector database 检索。
- 明确 MCP 工具当前体现为受治理的工具来源类型，并走统一工具治理链路；真实外部 MCP client 执行不属于当前 MVP，除非另起变更明确纳入。
- 明确控制台当前开放轻量管理动作，例如工具启停、知识源撤销、Skill 审批/发布/禁用；console 专用知识删除、Skill rollback REST/UI、完整工具注册和授权编辑仍是后续决策。
- 明确生产持久化以 MySQL/JDBC 为主，Redis 只覆盖 AgentScope state 和预算计数；development 的 `local-json` 或内存实现不代表生产 ready。
- 明确当前可验收的生产 snapshot 后端是 JDBC；OSS/S3/MinIO 只是对象存储扩展入口，必须有真实实现和健康检查后才能声明生产可用。
- 对齐 `docs/continue.md`、`docs/start.md`、`docs/release-readiness.md`、`web/README.md` 和 OpenSpec artifact 中的 supported/not-supported 表述。
- 补充历史 completed change specs 的 delta，收窄其中容易被理解为“当前完整可用”的 RAG、MCP、控制台、snapshot 和 Redis 业务持久化要求。
- 这个变更不修改运行时行为、公共 API 契约、数据库 schema 或前端工作流。

## Capabilities

### New Capabilities

- `mvp-boundary-governance`：定义项目如何记录 MVP 支持能力、显式非目标、生产接线要求和后续扩展点，并约束 OpenSpec 与面向运维/前端的文档保持一致。

### Modified Capabilities

- `admin-ops-console`：收窄控制台工具管理、知识源管理和 Skill 管理的当前可验收范围，明确 console delete、Skill rollback 和完整工具编辑不属于当前 REST/UI。
- `knowledge-rag`：收窄当前检索语义，明确 MVP 使用受权限治理的轻量词法检索，真实 embedding、vector DB 和 reranking 是后续范围。
- `governed-tool-calling`：收窄 MCP 当前语义，明确当前可验收的是受治理工具来源，不是真实外部 MCP client/server 调用。
- `production-runtime`：收窄生产分布式状态和 sandbox snapshot 表述，明确业务持久化以 MySQL/JDBC 为主，Redis 和对象存储 snapshot 的当前边界。
- `durable-persistence`：收窄 durable implementation 和 snapshot store 表述，明确 Redis 与对象存储后端的当前适用范围和 readiness 判定。

## Impact

- 影响 `clarify-mvp-boundaries` 的 OpenSpec artifacts。
- 影响 `docs/continue.md`、`docs/start.md`、`docs/release-readiness.md` 和 `web/README.md` 中关于 MVP 边界的措辞。
- 不需要 Java、React、数据库迁移、外部依赖或基础设施变更。
- 降低发布和交接风险，避免运维、产品或开发误以为真实向量检索、外部 MCP 执行、对象存储 snapshot、Redis 业务 store 或禁用的控制台动作已经生产可用。
