## 1. OpenSpec artifact 完整性

- [x] 1.1 将 `proposal.md` 调整为中文，并说明本变更会同时新增 `mvp-boundary-governance` 和收窄历史宽泛 specs。
- [x] 1.2 将 `design.md` 调整为中文，并记录为什么需要补充历史 completed specs 的 delta。
- [x] 1.3 创建 `specs/mvp-boundary-governance/spec.md`，覆盖 MVP 边界声明、RAG、MCP、控制台、持久化、snapshot、production readiness、未来变更门禁和文档矩阵验收。
- [x] 1.4 创建历史 capability delta specs，覆盖 `admin-ops-console`、`knowledge-rag`、`governed-tool-calling`、`production-runtime` 和 `durable-persistence`。

## 2. 历史规格收窄

- [x] 2.1 收窄 `admin-ops-console` 中工具管理、知识库管理和 Skill 管理的当前可验收范围。
- [x] 2.2 收窄 `knowledge-rag` 中文档处理、混合检索和知识删除/失效的当前可验收范围。
- [x] 2.3 收窄 `governed-tool-calling` 中 MCP 工具接入的当前可验收范围。
- [x] 2.4 收窄 `production-runtime` 中分布式状态和 sandbox snapshot 的当前可验收范围。
- [x] 2.5 收窄 `durable-persistence` 中 profile-aware durable implementation、snapshot store 和 readiness 校验的当前可验收范围。

## 3. 面向使用者的文档矩阵

- [x] 3.1 更新 `docs/continue.md`，将 8 个疑问点整理为当前判断、待决策项和建议收敛动作。
- [x] 3.2 更新 `docs/start.md` 的 RAG 章节，明确当前 `vectorScore` 是 token overlap/Jaccard 风格轻量评分，不是 embedding/vector DB/reranking。
- [x] 3.3 更新 `docs/start.md` 的工具治理章节，明确 MCP 当前是受治理工具来源类型，不代表真实外部 MCP client/server 执行。
- [x] 3.4 更新 `docs/start.md` 的控制台和 Skill 章节，明确 console delete、Skill rollback REST/UI、完整工具编辑不属于当前可执行入口。
- [x] 3.5 更新 `docs/start.md` 的生产运行时和部署章节，明确当前可验收 snapshot 后端是 JDBC，OSS/S3/MinIO 是后续扩展，Redis 当前只覆盖 AgentScope state 和 budget counter。
- [x] 3.6 更新 `docs/release-readiness.md`，明确 MCP 当前验收的是受治理工具来源，生产 snapshot 当前可验收后端是 JDBC，Skill rollback 当前不通过 console REST/UI 执行。
- [x] 3.7 更新 `web/README.md`，明确 console 只开放 revoke、Skill approve/publish/disable、工具启停，并说明本地 H2/local-json/in-memory 不代表 production durable readiness。

## 4. 一致性和校验

- [x] 4.1 搜索文档中关于 RAG、MCP、控制台管理、Redis、snapshot、production readiness 和未来扩展变更门禁的表述，确认没有明显冲突暗示。
- [x] 4.2 运行 `openspec validate clarify-mvp-boundaries`，确认 OpenSpec artifact 格式和需求场景通过校验。
- [x] 4.3 查看 `openspec status --change clarify-mvp-boundaries`，确认 proposal、design、specs 和 tasks 均已生成。
- [x] 4.4 确认本变更只做文档和规格治理，不包含 Java、React、数据库或基础设施实现改动。
