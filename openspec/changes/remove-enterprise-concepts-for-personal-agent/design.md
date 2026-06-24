## Context

`build-personal-agentscope-agent` 已经把产品目标切到个人 Agent，但当前仓库仍有大量企业平台时代的公共契约和内部命名：`tenantId`、`TenantStateKeyStrategy`、`SecurityPrincipal`、`roles`、`departments`、enterprise/admin/ops/audit/release gate 视图和文档。现有 `docs/enterprise-to-personal-migration-inventory.md` 把这些概念标记为兼容或诊断，但用户现在要求进一步收敛为单纯个人 Agent，不再让企业概念继续存在于默认产品、代码语言和文档中。

这不是一次文案替换。`tenant/user/role/department` 已经贯穿 API、状态 key、数据库 schema、RAG 权限、工具审计、telemetry、前端身份面板和测试夹具。迁移必须同时处理公共 API 破坏性变更、历史数据转换、回滚边界和回归测试。

## Goals / Non-Goals

**Goals:**

- 将个人版公共契约收敛到 owner、agent、session、workspace、memory、tool、skill、trace 等个人 Agent 语义。
- 删除默认 API/UI/文档中的企业租户、企业 RBAC、部门/角色、企业审批、管理后台、运维报表和发布门禁概念。
- 将仍需短期存在的旧入口隔离到 legacy adapter 或 diagnostics，并明确默认关闭、不可作为个人版验收路径。
- 将持久化和状态维度从 `tenant_id` / `user_id` 迁移到 owner 语义，提供备份、迁移和回滚策略。
- 保留个人 Agent 必需的安全能力：Prompt Injection guard、敏感信息脱敏、个人授权、工具确认、沙箱、幂等和最小本地审计。
- 增加自动化检查，防止企业术语重新进入个人版主路径。

**Non-Goals:**

- 不保留企业多租户平台、企业 RBAC、部门 ACL、企业审计报表或运维控制台作为默认产品能力。
- 不在本变更中扩展新的云厂商、企业 IdP、组织管理或多人协作模型。
- 不为了兼容旧客户端而继续污染主 API；兼容只能通过隔离 adapter 或一次性迁移处理。
- 不删除安全审计、工具确认、RAG 引用、状态恢复等个人 Agent 仍然需要的治理能力。

## Decisions

### Decision 1: 建立个人版唯一身份模型

个人版 SHALL 使用 `OwnerIdentity` / `OwnerScope` 作为唯一默认身份概念。业务层命令、领域模型和服务接口不再接收 `tenantId`、`roles` 或 `departments`，而是显式使用 `ownerId`、`agentId`、`sessionId` 和必要的 workspace/resource id。

现有 `RuntimeContextScope`、`SecurityPrincipal`、`RetrievalPrincipal`、`ToolPrincipal` 等对象需要迁移到个人语义命名。日志字段也应从 `tenantId`、`userHash` 迁移到 `ownerHash`、`agentId`、状态和 reason code。

替代方案：

- 保留 `tenantId=personal` 作为内部 key：短期成本低，但企业术语会继续泄漏到业务代码和测试。
- 直接删除所有身份维度：代码最简，但会破坏 owner/agent/session 隔离和状态恢复。

### Decision 2: 公共 API 原地破坏性切换到个人契约

个人版 API SHALL 原地切换为个人契约，请求体和查询参数默认不再包含 `tenantId`、`roles`、`departments`。例如聊天入口只需要 owner、agent、session、message、knowledge/tool/skill 选项；工具、知识、技能、工作区和 trace API 也使用 owner scope。

旧 enterprise-shaped 请求如果必须支持，应进入 `legacy` 包或单独 controller，并默认关闭。legacy adapter 负责把旧字段转换为新 owner scope，同时记录迁移告警；它不得被前端默认调用，不得出现在个人版文档主流程中。

替代方案：

- 新增 `/api/personal/**` 并长期保留旧 `/api/**`：迁移更平滑，但会让两套契约长期并存。
- 只改前端不改后端 DTO：用户体验改善有限，代码仍被企业契约牵制。

### Decision 3: 持久化 schema 迁移到 owner scope

数据库和本地状态文件 SHALL 迁移到 owner 语义。目标 schema 使用 `owner_id`、`agent_id`、`session_id`、`workspace_id`、`resource_id` 等列；不再在新表、新索引或新 store API 中使用 `tenant_id`。历史 `user_id` 若表示个人 owner，应迁移或重命名为 `owner_id`。

迁移策略：

1. 增加 migration，将已有 `tenant_id='personal'` 与 `user_id` 转换为 `owner_id`。
2. 对仍需保留历史值的记录，写入一次性 migration metadata 或 archive 表，而不是继续把 tenant 暴露给业务层。
3. 移除或替换 `allowed_roles_json`、`allowed_departments_json` 等企业 ACL 列；个人可见性使用 owner、agent、source visibility 和授权记录表达。
4. 本地 JSON/Redis/AgentScope state key 从 `tenant:<...>` 迁移到 `owner:<...>`，并提供旧 key 读取迁移测试。

替代方案：

- 只改 Java 字段名，不改 DB 列：实现更快，但 schema 文档和运维仍保留企业语义。
- 一次性 drop 所有旧数据：最简单，但不接受，因为会丢失会话、记忆、工具确认和快照。

### Decision 4: 企业 RBAC 替换为个人授权和本地审计

安全层 SHALL 删除默认企业 RBAC、部门/角色数据权限和企业审批人流程。个人 Agent 授权由以下能力组成：

- owner 对自己的 Agent、workspace、memory、tool、skill 和 trace 有默认管理权。
- 工具、技能、Shell/code/SQL/network 等副作用能力使用 allow、confirm、deny 三态。
- 高风险操作继续走 HITL、参数展示、沙箱、幂等和最小审计。
- RAG 访问使用 owner/agent/source visibility，不再使用部门/角色 ACL。

保留的安全能力需要重命名为个人语义，例如 `PersonalAuthorizationService`、`OwnerIdentityResolver`、`PersonalAuditRecord`。

替代方案：

- 保留企业 RBAC 做“高级模式”：会继续扩大测试矩阵，不符合单纯个人 Agent 定位。
- 完全删除审计：能简化代码，但工具确认、回滚和故障排查会失去证据链。

### Decision 5: 前端只保留个人工作台主路径

`web/` SHALL 移除默认 IdentityPanel 中的租户、角色、部门输入，导航不再展示 Admin、Operations、Release 等企业平台视图。个人工作台保留并强化聊天、计划、文件、知识/记忆、工具、技能、Agent 配置和 trace/diagnostics。

旧视图如果短期仍需开发诊断，必须移动到明确的 diagnostics/legacy 路径，默认导航隐藏，并在文档中标明不属于个人版产品路径。

替代方案：

- 仅改文案保留页面结构：改动小，但 admin/ops/release 信息架构仍会误导用户。
- 删除所有诊断视图：产品更纯粹，但维护者失去迁移期排障入口。

### Decision 6: 文档和测试成为术语门禁

本变更 SHALL 增加自动化检查，扫描个人版主路径中的禁止术语，例如企业租户、RBAC、部门、角色、admin/ops/release gate、enterprise console 等。允许列表只覆盖迁移文档、legacy adapter、数据库 migration 和明确历史说明。

后端测试应覆盖无企业字段请求、owner/agent/session 隔离、旧数据迁移、工具确认、RAG 可见性、Skill 权限和 trace 归属。前端测试应覆盖默认 UI 不展示企业身份或企业导航。

替代方案：

- 依靠 code review 手工检查：容易回流。
- 全仓库禁止所有历史词：过于激进，会影响 migration、release notes 和归档文档。

## Risks / Trade-offs

- [破坏旧 API 客户端] -> 明确这是 breaking change；如需过渡，只提供默认关闭的 legacy adapter，并记录迁移告警。
- [持久化迁移丢数据] -> 每个 schema/store migration 必须有备份说明、迁移测试和回滚方案，覆盖 session、memory、tool pending confirmation、audit、telemetry、snapshot 和 Agent state。
- [只改名称不改语义] -> specs 和任务必须要求 API、UI、service command、store API、测试夹具和文档一起迁移。
- [安全能力被误删] -> 企业 RBAC 可以移除，但 Prompt Injection guard、脱敏、工具确认、沙箱、幂等和个人审计必须保留。
- [legacy adapter 长期滞留] -> 设定默认关闭和删除任务，adapter 不能被个人工作台调用。
- [术语扫描误报] -> 使用 allowlist 区分 migration/legacy/archive 文档与个人版主路径。

## Migration Plan

1. 盘点术语和入口：生成 enterprise term inventory，按 public API、domain/service、persistence、frontend、docs、tests 分类。
2. 建立个人命名模型：新增 owner scope、personal context、personal authorization 和 personal audit 的目标类型。
3. 切换 API 契约：更新 request/response、controller、client types 和测试，个人请求不再传 tenant/role/department。
4. 迁移持久化：增加 Flyway 和本地 state migration，重命名或替换 `tenant_id` / `user_id` / ACL JSON 字段，保留备份和旧 key 迁移验证。
5. 清理服务层：替换 RuntimeContext、RAG principal、tool permission、skill governance、telemetry、budget、snapshot 等服务中的企业命名和企业权限判断。
6. 清理前端：移除身份面板企业字段和企业导航，保留个人工作台与必要 diagnostics。
7. 清理文档：更新 README、docs、web README 和 OpenSpec 文档，迁移历史只保留在明确的 legacy/migration 文档中。
8. 增加术语门禁和回归测试：后端、前端、migration 和扫描脚本都必须通过后才能认为完成。

回滚策略：

- 代码发布前必须备份数据库和本地 state/workspace 目录。
- 若 schema migration 已执行，回滚旧服务前必须恢复备份或运行反向 migration；不能让旧服务读取已去 tenant 的 schema。
- legacy adapter 可作为短期请求回退入口，但不能替代数据恢复方案。

## Documentation And Release Boundary

- `README.md`、`AGENTS.md`、`docs/start.md`、`docs/release-readiness.md` 和 `web/README.md` 是默认产品文档，必须使用 owner、Agent、session、workspace、memory、tool、skill 和 trace 口径。
- 旧企业概念、旧字段、旧状态 key、旧表名和旧接口形态只允许出现在 migration、legacy、archive、diagnostics 或 schema 迁移文档中，并且不得作为个人版主路径示例。
- 发布手册必须说明这是 schema/state/API breaking change：发布前备份数据库、本地 state、Redis、workspace、snapshot 和工具确认记录；回滚旧服务前必须恢复备份或完成演练过的反向迁移。
- 默认术语门禁命令使用 `node scripts/validate-personal-terms.mjs`。它扫描个人主路径并仅通过 allowlist 放行 migration/legacy/archive/diagnostics 语境。
- 旧诊断入口和 legacy adapter 只能短期保留，必须默认隐藏或默认关闭；个人工作台、默认 API client 和默认 smoke 不得调用它们。

## Open Questions

- 是否接受删除所有旧 enterprise-shaped REST endpoint，还是需要一个默认关闭的 legacy adapter 保留一个版本周期？
- `ha_*` 表是否直接原地重命名列，还是新建个人版表并迁移数据后删除旧表？
- 当前 `/api/release/**` 是直接删除，还是移动到 diagnostics 并更名为个人版 smoke/readiness？
- 术语扫描的 allowlist 应覆盖哪些目录：`openspec/archive`、历史变更、migration SQL、legacy adapter、迁移说明是否都允许？
- 个人版是否继续保留“audit”一词，还是统一改为 activity/trace 并只在底层表迁移中保留历史名？
