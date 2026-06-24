# HarnessAgent 个人版发布与验收手册

## MVP 业务场景

当前产品目标是个人版完整 Agent。首个个人版验收场景选择：个人知识和工作区助手。

目标用户：

- 个人 owner 通过聊天和工作区完成资料整理、文件处理、知识问答和任务规划。
- 个人 owner 管理本地知识源、记忆、工具、技能和 Agent 配置。
- 维护者检查本地/生产配置、持久化、沙箱、trace、最小记录和回滚能力。

MVP 验收标准：

- 用户可以通过 `POST /api/chat` 和 `POST /api/chat/stream` 完成制度问答。
- RAG 启用时，回答必须带引用来源；无可访问证据时返回无答案。
- 不同 owner、Agent、会话和工作区之间不能共享状态、历史、知识、记忆、工具授权或 trace。
- 高风险工具、技能、代码、Shell、SQL、外部写操作和网络副作用需要个人确认或沙箱策略。
- 工作台能够展示聊天、计划、工作区文件、知识/记忆、工具确认、技能、Agent 配置和 trace。
- 早期集中治理和阶段准入能力只作为历史兼容或诊断，不作为个人版主验收目标。

## 个人版阶段验收

| 阶段 | 通过条件 | 回滚开关 |
|---|---|---|
| Personal Core | owner/agent/session 隔离、非流式/流式聊天、模型抽象、预算、超时和 fallback 通过测试 | 禁用新个人 Agent 或切回 echo provider |
| Workspace | 工作区初始化、路径拒绝、文件引用、快照恢复、计划模式、channel 和压缩通过测试 | 禁用工作区写操作或切回上一 workspace backend |
| Memory/RAG | 个人知识源、记忆、索引状态、引用来源、无答案、删除和导出通过测试 | 关闭 RAG provider 或禁用目标知识源 |
| Tool/HITL | 参数拒绝、只读允许、写操作确认、HITL 恢复、结构化结果、幂等和最小记录通过测试 | 禁用单个工具或全部高风险工具 |
| Skill | 本地技能仓库、元数据、加载、权限、启停、升级、回滚和验证通过测试 | 禁用、回滚或锁定目标 Skill 版本 |
| Multi-Agent | 子 Agent 规格、路由、后台委派、Agent-as-Tool、上下文边界、trace 和失败降级通过测试 | 禁用 Supervisor，回退单 Agent |
| Workbench | 聊天、计划、文件、知识/记忆、工具、技能、配置、trace 在桌面和移动视口通过测试 | 回退上一版 Web 静态资源或在网关隐藏个人工作台入口 |

## 历史准入诊断处理

历史准入诊断接口仍可用于检查早期生产 wiring，但它们不是个人版完整 Agent 的验收依据。个人版发布必须以本手册的 owner 隔离、工作区、记忆/RAG、工具确认、Skill、trace 和回滚清单为准，并结合 AgentScope Java v2 覆盖矩阵和迁移盘点文档。

## 部署回滚流程

1. 禁用入口能力：
   - 回退上一版 Web 静态资源，或在网关把个人工作台入口切回旧 console。
   - 禁用目标 Agent。
   - 禁用目标工具。
   - 关闭 RAG-backed answer。
   - 禁用 Supervisor 多 Agent 编排。

2. 切换运行依赖：
   - 切回旧模型 provider。
   - 切回上一版配置。
   - 个人 Skill 使用 `/api/skills/{skillName}/{version}/rollback` 回滚到上一可用版本，必要时再 lock；遗留 console Skill API 只保留禁用能力。

3. 保留证据：
   - 不删除工具最小记录。
   - 不删除安全最小记录。
   - 不删除编排 trace。
   - 标记回滚原因、操作者和时间。

4. 验证恢复：
   - 重新执行 owner/Agent/session 隔离、工作区路径拒绝、高风险确认、trace 和健康检查。
   - 确认新请求不再进入被回滚能力。

## 个人版回滚操作表

| 能力 | 禁用或回滚方式 | 验证方式 |
|---|---|---|
| 个人工作台入口 | 当前没有运行时路由 feature flag。发布时必须保留上一版 Web 静态资源或网关路由；回滚时将 `/`、`/assets/**` 和 Vite 静态资源指向上一版 console，或在网关临时拒绝个人工作台入口。 | 浏览器打开首页，确认不再出现 Chat、Tasks、Files、Knowledge、Tools、Agent 和 Trace 个人工作台导航；后端 API 可以继续保留用于兼容和诊断。 |
| 工具 | 通过 `PATCH /api/console/tools/{toolId}/enabled` 将目标工具置为 `false`。对 Shell、SQL、code、network、`MCP`、`PROTOCOL` 或外部写操作工具，先列出工具并按 `sourceType`、`sourceRef` 或风险等级批量禁用。 | `GET /api/console/tools` 中目标工具 `enabled=false`；再次执行 `/api/tools/execute` 应返回禁用或拒绝结果，并保留最小记录。 |
| Skill | 个人 Skill 使用 `/api/skills/{skillName}/{version}/disable` 禁用异常版本，使用 `/api/skills/{skillName}/{version}/rollback` 回滚到上一可用版本，稳定后可用 `/lock` 锁定。 | `GET /api/skills` 中目标版本状态符合预期；触发禁用版本应失败，触发回滚版本应通过权限和路径校验。 |
| RAG provider | 请求级关闭使用 `knowledgeEnabled=false`；配置级回滚将 `harness-agent.memory-rag.provider` 切回 `local` 或移除外部 provider 配置；数据级隔离使用 `PATCH /api/console/knowledge/{sourceId}/revoke` 撤销目标知识源，避免优先删除数据。 | 聊天请求不再注入 RAG evidence；被撤销知识源不再出现在检索结果；未知外部 provider 应 fail closed。 |
| 协议适配器 | `MCP` 和 `PROTOCOL` 当前作为受治理工具来源接入。回滚时按 `sourceType` 或 `sourceRef` 禁用对应工具，并同步撤销外部 adapter 配置、网关访问或本地凭据。 | 列表中相关工具为禁用；协议工具执行被拒绝；最小记录仍保留禁用前后的 source type/source ref。 |
| 多 Agent 编排 | 禁用 Supervisor 入口或将请求回退单 Agent；禁用 `AGENT` source type 的 Agent-as-Tool 工具。 | trace 中不再产生新 handoff、subagent 或 background delegation 记录。 |

常用命令示例，替换其中 owner、agent、tool、skill 和 source ID：

```bash
curl -s "http://localhost:8080/api/console/tools?ownerId=owner-a" \
  -H "X-Owner-Id: owner-a"

curl -X PATCH "http://localhost:8080/api/console/tools/tool-id/enabled?ownerId=owner-a" \
  -H "Content-Type: application/json" \
  -H "X-Owner-Id: owner-a" \
  -d '{"enabled":false}'

curl -X PATCH "http://localhost:8080/api/skills/skill-name/1.0.0/disable?ownerId=owner-a" \
  -H "X-Owner-Id: owner-a"

curl -X PATCH "http://localhost:8080/api/skills/skill-name/0.9.0/rollback?ownerId=owner-a" \
  -H "X-Owner-Id: owner-a"

curl -X PATCH "http://localhost:8080/api/console/knowledge/source-id/revoke?ownerId=owner-a" \
  -H "X-Owner-Id: owner-a"
```

发布前必须形成回滚清单：个人工作台上一版静态资源位置、待禁用工具 ID、`MCP`/`PROTOCOL` source ref、Skill 当前版本和上一版本、RAG provider 当前值、可撤销知识源 ID，以及回滚操作者。

## 端到端验收清单

- 个人隔离：
  - owner-a 的会话、知识源、记忆、工具授权和 trace 不能被 owner-b 看到。

- 个人授权：
  - 工作区外路径访问被拒绝。
  - 禁用工具和技能不能被 Agent 执行。
  - 无授权知识源和记忆不能进入 prompt 或检索结果。

- 高风险确认：
  - 高风险工具未确认时返回 `PENDING_CONFIRMATION`。
  - 确认后执行并记录审批人或确认上下文。
  - 同 idempotency key 同参数复用结果，不同参数返回冲突。

- 最小记录可追溯：
  - Agent 配置更新、工具确认、Skill 版本变更、多 Agent 委派都有脱敏记录。
  - 最小记录中的 token、邮箱、手机号、API key、prompt、工具原始结果和文件内容不会明文落库。

- Trace 可观测：
  - API、Agent、RAG、工具、子 Agent、Token、失败、耗时和反馈能进入个人 trace 或诊断视图。
  - 用量能按 owner、Agent 和 provider 聚合；历史聚合报表只作为迁移诊断。

## 本次端到端验收证据

2026-06-23 的个人版发布候选以以下自动化检查作为验收证据。本次去旧概念变更完成后，发布记录必须附上术语门禁、OpenSpec 校验、后端测试、前端单元测试、浏览器测试和前端构建结果。

| 验收项 | 自动化证据 | 边界 |
|---|---|---|
| 个人聊天和流式事件 | `mvn test` 中的 `ChatServiceTest`、`ApiContractTest`，以及 `web/tests/console.spec.ts` 聊天工作台流程 | Playwright 使用 mock 后端，不替代真实环境 smoke。 |
| 工作区文件 | `PersonalWorkspaceServiceTest`、`PersonalWorkspaceFileServiceTest`、`WorkspaceController` API client 测试和 Playwright 文件视图 | 真实对象存储仍是后续扩展，当前生产快照验收路径是 JDBC。 |
| 记忆/RAG | `PersonalMemoryServiceTest`、`KnowledgeServiceTest`、`MemoryRagProviderRegistryTest` 和前端知识/记忆视图测试 | 外部 memory/RAG provider 未配置时应 fail closed。 |
| 工具确认 | `ToolServiceTest`、`JdbcToolStoreTest`、前端 confirmation resume 测试和 Playwright 确认流程 | 默认 executor 是治理链路回显，真实外部系统工具需额外接线验收。 |
| 子 Agent | `WorkspaceSubAgentSpecStoreTest`、`OrchestrationServiceTest` 和 trace 视图测试 | 后台委派队列跨进程恢复仍是后续增强。 |
| Skill | `PersonalSkillServiceTest`、`SkillControllerTest`、前端 API client 和工作台 Skill 管理测试 | Git/MySQL/PostgreSQL 远端 Skill 仓库仍是 adapter 预留。 |
| 工作台诊断 | `web/src/App.test.tsx`、`web/src/navigation.test.ts`、`web/tests/console.spec.ts` 和 `npm run build` | 历史准入接口只作诊断，不作为个人版验收口径。 |

## 2026-06-24 去企业概念发布准备记录

本次去旧概念变更的自动化验收：

| 检查 | 结果 |
|---|---|
| 企业术语门禁 | `node scripts/validate-personal-terms.mjs` 通过。 |
| OpenSpec 校验 | `openspec validate <change-id>` 通过。 |
| 后端回归 | `mvn -q test` 通过，覆盖个人 API、owner 隔离、持久化迁移、旧 key 迁移、RAG、工具、Skill、trace 和回滚边界。 |
| 前端单元测试 | `cd web && npm run test:unit` 通过。 |
| 前端浏览器测试 | `cd web && npm run test:browser` 通过，覆盖个人工作台桌面/移动导航、聊天、工具确认、typed events 和文件引用。 |
| 前端构建 | `cd web && npm run build` 通过。 |

迁移 smoke 覆盖范围：

- 旧数据备份和 schema migration：`DurablePersistenceMigrationTest` 从 V10 seed 旧表/旧列，再迁移到 V14，验证 owner columns、activity 表、owner-keyed state、budget key 和 migration marker。
- state 迁移：`JdbcAgentStateStoreTest` 验证旧 key 读取后迁移到 `owner:<ownerId>:agent:<agentId>:session:<sessionId>:scope:<scope>`，后续写入不再生成旧 key。
- 个人主流程：`ChatServiceTest`、`KnowledgeServiceTest`、`ToolServiceTest`、`PersonalSkillServiceTest`、`OrchestrationServiceTest` 覆盖个人聊天、工作区/RAG、工具确认、Skill 和 trace。
- 个人工作台启动：Playwright desktop/mobile smoke 覆盖聊天、文件、知识/记忆、工具、Skill、Agent 配置和 trace/diagnostics 视图。

短期保留项和删除计划：

- `src/main/java/com/harnessagent/api/legacy/**` 的 request adapter 默认关闭，只用于旧请求格式兼容；确认无旧调用后在下一个兼容窗口删除。
- `/api/diagnostics/readiness`、`web/src/legacy/**` 和历史 release/readiness 说明仅作诊断或迁移参考，不进入个人版默认导航；完成一次生产 cutover 后删除旧 Web 视图。
- 持久化层保留历史 scope/user 兼容列和旧术语注释，用于 rollback、archive 和数据核对；确认备份、反向迁移脚本和新服务稳定后，再规划破坏性 schema 清理。
- `TenantStateKeyStrategy` 仅保留给旧 key 迁移测试和兼容读取；旧 key 命中率连续一个发布周期为零后删除。

## 发布前命令

```bash
node scripts/validate-agentscope-coverage.mjs
node scripts/validate-personal-terms.mjs
openspec validate <change-id>
mvn test
cd web && npm run test:unit
cd web && npm run test:browser
cd web && npm run build
```

The terminology gate is part of the personal-only release checks. During the migration cleanup, use its inventory mode to track remaining cleanup; the strict gate must pass before release.

如果 `mvn test` 报 `无效的标记: --release`、`class file version 61.0` 或 Spring Boot Maven 插件无法加载，说明当前 Java 版本低于 17。先切换 JDK 17，并确认 `mvn -version` 中的 Java version 也是 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

如果 macOS 没有把 Homebrew JDK 注册到 `java_home`，但已经安装 `openjdk@17`：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

后端启动 smoke test：

```bash
mvn -o -Dmaven.test.skip=true spring-boot:run
curl -i 'http://localhost:8080/api/console/user?ownerId=owner-a&agentId=personal-assistant'
```

Durable persistence 发布前检查：

```bash
curl -s 'http://localhost:8080/api/sessions?ownerId=owner-a&agentId=personal-assistant'
```

检查返回中生产运行时状态：

- `status` 必须为 `PASSED`。
- `checks` 应包含 `distributed-state`、`workspace`、`telemetry`、`budget`、`timeout`、`snapshot`。
- `failureReasons` 必须为空。

DB schema and metadata comment migration 验收：

- Flyway locations 必须包含 `classpath:db/migration,classpath:db/vendor-migration/{vendor}`。
- H2 路径由 `DurablePersistenceMigrationTest` 覆盖，确认公共 V1/V3/V5/V7/V9/V11/V13 与 H2 V2/V4/V6/V8/V10/V12/V14 可执行，并能读取 17 张表和 204 个字段 comments。
- MySQL 发布前需在目标 schema 执行 [durable-persistence-schema.md](durable-persistence-schema.md) 中的 `information_schema.tables` / `information_schema.columns` 查询，确认 17 张表均有 `table_comment`，且字段 comment 缺失查询无返回。
- MySQL vendor comment migration 使用 `MODIFY COLUMN ... COMMENT`，发布前需由 DBA 或 schema owner 审核字段类型、NULL 约束和 `AUTO_INCREMENT` 语义未被改变。
- V2/V4/V6/V8/V10/V12/V14 只增加 metadata comments。回滚优先 roll-forward 到后续 vendor comment migration；除非已有数据库备份和停写窗口，不通过删除 durable 表、回退 V1/V3/V5/V7/V9/V11/V13 DDL 来处理 comment 文案问题。

生产 profile 必填配置：

- `HARNESS_AGENT_MYSQL_DSN`
- `HARNESS_AGENT_DB_USERNAME`
- `HARNESS_AGENT_DB_PASSWORD`
- `HARNESS_AGENT_DURABLE_STATE_WIRED=true`
- `HARNESS_AGENT_DURABLE_TELEMETRY_ENABLED=true` 或 `HARNESS_AGENT_OTEL_ENABLED=true`

如果启用 Redis-backed AgentScope state 或 budget counter，还需要：

- `HARNESS_AGENT_REDIS_URI`
- `harness-agent.production.state-store.type=redis` 或 `harness-agent.production.durable-stores.budget-counter=redis`

如果启用 sandbox/code workload，还需要：

- `HARNESS_AGENT_SANDBOX_ENABLED=true`
- `HARNESS_AGENT_SNAPSHOT_STORE_TYPE=jdbc`
- `HARNESS_AGENT_SNAPSHOT_STORE_URI=jdbc://snapshot`
- `HARNESS_AGENT_SNAPSHOT_STORE_WIRED=true`

当前可验收的生产 snapshot 后端是 JDBC；OSS/S3/MinIO 属于后续对象存储扩展，不能只配置枚举值就视为生产可用。

Owner-scope state migration 发布检查：

- 发布前备份数据库、local JSON Agent state 目录、Redis dump/snapshot、workspace、snapshot 和工具确认记录。
- 确认 `ha_agent_state` 新写入的 `state_key` 使用 `owner:<ownerId>:agent:<agentId>:session:<sessionId>:scope:<scope>`。
- 对迁移样本执行一次旧 key 读取，确认旧形态 key 会被写入 owner key，并且后续写入不再生成旧 key。
- 如果同时存在旧 key 和 owner key，以 owner key 为准；发布 smoke 应验证不会被旧值覆盖。
- 旧服务不能直接读取 owner-keyed state。回滚旧服务前必须恢复发布前备份，或在停写窗口内执行经过演练的反向迁移。

回滚注意事项：

- 回滚服务版本前先停止新版本写流量，避免同一 schema 同时被两套模型写入。
- 回滚配置时优先切回上一版 `application-production.yml` 或环境变量。
- 不删除安全记录、个人记忆、知识源、知识切片、工具记录、幂等、待确认操作、telemetry、snapshot metadata 和 snapshot content。
- 如果必须恢复数据库备份，先验证备份同时包含 session、AgentScope state、snapshot、idempotency、pending confirmation、telemetry 和 owner migration marker 表。
