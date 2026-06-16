我刚做的是静态扫描，没跑完整测试。早期 `enterprise-agent-platform`、`add-web-ui-console`、`add-durable-persistence` 三个企业 MVP 变更已经完成，但当前产品方向已切换到 `build-personal-agentscope-agent`：个人版完整 Agent 是目标，企业租户、RBAC、运营后台、企业审计报表和发布门禁只作为遗留兼容或开发诊断。下面这些不应理解为“个人版任务已完成”，而是迁移前的企业 MVP 边界、未开放入口或生产接线待决策点。

个人版迁移时先读：

- [enterprise-to-personal-migration-inventory.md](enterprise-to-personal-migration-inventory.md)
- [agentscope-java-v2-coverage.md](agentscope-java-v2-coverage.md)
- [release-readiness.md](release-readiness.md)

## 当前判断

- 早期 OpenSpec 任务状态已经闭环：`openspec list --json` 显示三个企业 MVP 变更均为 `complete`。这表示早期企业 MVP 规格内的实现和测试任务已完成，不等于个人版完整 Agent 已完成，也不等于任意生产部署无需配置即可 ready。
- RAG 已具备知识源、切片、租户和权限过滤、引用、无答案、指标和反馈能力；边界在于当前 `vectorScore` 是基于 token overlap/Jaccard 的轻量 lexical score，不是真实 embedding + vector DB 检索。
- 工具治理链路已覆盖权限、参数校验、Prompt 安全检查、高风险确认、幂等和审计；边界在于默认工具执行器是通用回显执行器，MCP 目前主要体现为受治理的 `sourceType`，仓库内未见真实 MCP client 或外部系统调用适配器。
- 控制台已覆盖核心只读/轻量管理动作；个人版需要把这些入口迁移为个人工作台，旧 admin/ops/audit/release 视图只保留为兼容或诊断。
- 持久化生产路径以 MySQL/JDBC 为主，Redis 只覆盖 AgentScope state 和预算计数这类共享状态；development/test 的 `local-json` 或内存实现不能代表生产持久化已启用。

## 待决策疑问点

### 1. 控制台知识删除是否进入 MVP

现状：

- Console API 暴露知识源列表和 revoke，但没有 console 专用 delete endpoint。
- 前端知识源 `Delete` 按钮是禁用态，提示 `Console delete endpoint is not exposed`。
- 后端通用知识 API 已有 `DELETE /api/knowledge/sources/{sourceId}`，`ConsoleService.deleteKnowledge` 服务方法也存在，只是未通过 `ConsoleController` 暴露。

待决策：

- MVP 控制台是否只开放 revoke，不开放 delete？
- 如果开放 console delete，需要补齐哪些约束：管理员权限、二次确认、审计动作、与 revoke 的语义差异、误删恢复策略？
- 通用 `/api/knowledge/sources/{sourceId}` 是否应定位为后台 API，而不是控制台管理动作？

参考：`web/README.md:53`、`web/src/views/AdminWorkspace.tsx:326`、`src/main/java/com/harnessagent/api/ConsoleController.java:103`、`src/main/java/com/harnessagent/api/KnowledgeController.java:77`、`src/main/java/com/harnessagent/console/ConsoleService.java:187`。

### 2. Skill rollback 是否只保留为发布预案

现状：

- Console API 支持 Skill list、approve、publish、disable。
- 前端 `Rollback` 按钮是禁用态，提示没有 REST API。
- `SkillGovernanceService.rollback` 业务方法存在。
- `/api/release/rollback` 已存在，但它返回的是发布回滚动作清单，不是可执行 Skill rollback endpoint。

待决策：

- MVP 是否只在发布回滚预案中展示 Skill rollback，不提供可执行 REST/UI？
- 如果开放，需要定义 active version、target version、审批人、审计记录和状态流转。
- 回滚失败时是否需要保留旧版本可继续服务、或只允许 disable 可疑 Skill？

参考：`web/README.md:55`、`web/src/views/AdminWorkspace.tsx:371`、`src/main/java/com/harnessagent/api/ConsoleController.java:120`、`src/main/java/com/harnessagent/security/SkillGovernanceService.java:52`、`src/main/java/com/harnessagent/api/ReleaseController.java:34`。

### 3. 工具注册和授权编辑是否放进控制台

现状：

- 后端已有 `POST /api/tools` 注册工具，并支持 required/optional parameters、allowed users/agents/departments/roles、risk level、sensitive fields 等治理字段。
- 执行路径会校验权限、参数、确认、幂等和审计。
- Web admin 工具页当前只展示已有工具并切换 enabled；工具确认/拒绝走 `/api/tools/execute` 和 `/api/tools/reject`，不是完整工具注册/授权 UI。

待决策：

- MVP 控制台是否仅管理已注册工具的启停？
- 完整注册、参数 schema、授权策略编辑是否后置，并继续由 `/api/tools` API 承载？
- 如果放进控制台，是否需要审批流、变更 diff、回滚、测试调用和审计搜索联动？

参考：`web/README.md:56`、`web/src/views/AdminWorkspace.tsx:259`、`web/src/api/client.ts:259`、`src/main/java/com/harnessagent/api/ToolController.java:35`、`src/main/java/com/harnessagent/api/ToolRegistrationRequest.java:19`、`src/main/java/com/harnessagent/tooling/ToolService.java:180`。

### 4. RAG 是否需要真实 embedding 和向量库

现状：

- 当前检索按租户读取 chunk，在应用内计算分数、权限过滤并排序。
- `vectorScore` 实际是 `intersection / union`，也就是 token Jaccard 风格分数。
- JDBC 知识存储保存的是 chunk 和 `tokens_json`，未见 embedding 生成、向量索引或 vector DB adapter。

待决策：

- MVP 是否明确将当前 `vectorScore` 定义为轻量 lexical score？
- 如果要满足真实“关键词 + 向量 + 重排序”语义，是否需要新增 embedding provider、向量索引/DB、相似度检索 adapter 和离线重建流程？
- 引入真实向量检索后，权限过滤必须前置还是可在召回后强过滤？这会影响召回质量和数据隔离风险。

参考：`src/main/java/com/harnessagent/rag/KnowledgeService.java:85`、`src/main/java/com/harnessagent/rag/KnowledgeService.java:162`、`src/main/java/com/harnessagent/rag/TextTokenizer.java:11`、`src/main/java/com/harnessagent/rag/JdbcKnowledgeStore.java:132`。

### 5. MCP 是否需要真实外部执行适配

现状：

- `ToolSourceType` 包含 `MCP`，工具注册和审计能保留 source type/source ref。
- MCP 工具会走同一套权限、参数、确认、幂等和审计治理。
- `DefaultToolExecutor.supports()` 返回 true，执行结果只回显工具元数据、source ref、参数和时间戳。
- 现有测试覆盖 MCP 作为受治理工具来源，不覆盖真实 MCP server 调用。

待决策：

- MVP 的 MCP 能力是否只承诺“可注册为受治理工具来源”？
- 是否必须补齐真实 MCP client、连接配置、超时、重试、错误映射、结果脱敏和审计联动？
- 如果接入外部系统，工具执行失败、幂等冲突和高风险确认后的重放语义需要如何定义？

参考：`src/main/java/com/harnessagent/tooling/ToolSourceType.java:3`、`src/main/java/com/harnessagent/tooling/DefaultToolExecutor.java:12`、`src/main/java/com/harnessagent/tooling/ToolService.java:118`、`src/main/java/com/harnessagent/tooling/ToolService.java:180`、`src/test/java/com/harnessagent/tooling/ToolServiceTest.java:207`。

### 6. Snapshot MVP 是否只承诺 JDBC

现状：

- `SnapshotStoreType` 枚举包含 `OSS`、`S3`、`MINIO`、`JDBC`。
- 当前可验收的生产 snapshot 实现是 `JdbcSnapshotStore`。
- 健康检查只认可 JDBC；配置 OSS/S3/MINIO 会被判定为 `no production implementation is active`。

待决策：

- MVP 是否明确只支持 JDBC snapshot？
- OSS/S3/MINIO 是后续扩展，还是需要在当前发布前补齐至少一个对象存储后端？
- 对象存储后端如果后置，文档和配置示例是否需要避免暗示这些后端已经可用于生产？

参考：`src/main/java/com/harnessagent/production/SnapshotStoreType.java:3`、`src/main/java/com/harnessagent/production/JdbcSnapshotStore.java:17`、`src/main/java/com/harnessagent/production/JdbcSnapshotStore.java:29`、`src/main/java/com/harnessagent/production/DurablePersistenceHealthService.java:158`。

### 7. Redis 是否作为通用业务持久化后端

现状：

- Redis 实现覆盖 `AgentStateStore` 和 `BudgetCounterStore`。
- session、knowledge、tool、audit、telemetry 在生产健康检查中要求 JDBC 实现。
- 将这些业务 store 配置为 Redis 会被判定为 `Redis ... store is not wired`。
- `application-production.yml` 默认 durable stores 都是 `mysql`。

待决策：

- Redis 是否只定位为 AgentScope state 和预算计数的可选生产实现？
- 是否需要 Redis 版本的 session/knowledge/tool/audit/telemetry store？
- 如果不做 Redis 业务 store，文档应避免暗示 Redis 可承载全部生产数据。

参考：`src/main/java/com/harnessagent/production/RedisAgentStateStore.java:13`、`src/main/java/com/harnessagent/production/RedisBudgetCounterStore.java:8`、`src/main/java/com/harnessagent/production/DurablePersistenceHealthService.java:111`、`src/main/java/com/harnessagent/production/DurablePersistenceHealthService.java:222`、`src/main/resources/application-production.yml:12`。

### 8. 本地开发态是否能代表生产 ready

现状：

- 默认配置是 `profile: development`、`state-store.type: local-json`，durable stores 也是 `local-json`。
- development 配置里的 Redis/MySQL 是本地示例，不驱动真实 store wiring。
- production profile 仍需要 MySQL DSN、账号密码、durable state wired flag、telemetry，以及启用 sandbox 时的 snapshot 配置。

待决策：

- 文档是否需要把“本地 MVP 可运行”和“生产 readiness 通过”明确拆开？
- 发布验收是否必须要求使用 `application-production.yml` 和真实环境变量跑一轮 phase-gates？
- 是否需要增加一份最小生产配置清单，避免误把默认 H2/local-json 当成生产持久化？

参考：`src/main/resources/application.yml:36`、`src/main/resources/application-development.yml:3`、`src/main/resources/application-development.yml:10`、`src/main/resources/application-production.yml:3`、`src/main/resources/application-production.yml:46`。

## 建议收敛动作

1. 把上述疑问点按“个人版是否需要”逐项定性：进入个人版核心、作为 provider/接口扩展、仅保留遗留兼容说明。
2. 如果某项进入个人版核心，放入 `build-personal-agentscope-agent` 对应任务或后续 OpenSpec change，避免扩大早期企业 MVP 范围。
3. 如果某项后置，更新 `docs/start.md`、`docs/release-readiness.md` 或 `web/README.md` 的措辞，明确它是边界而不是隐藏能力。
4. 对个人版发布，至少验证 owner/Agent/session/workspace 隔离、AgentScope v2 覆盖矩阵、工作台主流程和生产 profile；旧 phase-gates 只能补充检查生产 wiring。
