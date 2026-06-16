# 企业到个人版迁移盘点

本文记录当前仓库中仍然存在的企业版概念，以及迁移到个人版完整 Agent 时的处理方式。盘点日期：2026-06-16。

## 处理原则

- `tenantId` 继续作为内部兼容隔离 key 存在，但个人版默认映射为字面量 `personal`，不作为用户必须理解或输入的企业租户；AgentScope-facing `runtimeUserId` 派生为 `personal:<ownerId>`。
- `userId` 在个人版语义中等价于 owner；状态链路仍保留 owner、agent、session 维度，避免不同个人 Agent 或会话串数据。
- `roles`、`departments`、企业 RBAC 和企业数据权限不再是个人版主流程。高风险行为改由个人授权、工具确认、沙箱和可撤销记录承载。
- admin/ops/audit 控制台和 release gate API 只作为迁移期诊断或兼容入口，不能作为个人版功能验收的主路径。
- 审计从企业合规报表降级为个人最小审计：记录高风险工具、技能变更、外部副作用、幂等冲突和恢复所需摘要，避免记录 prompt、文件内容、工具原始结果或密钥。

## 残留概念清单

| 领域 | 残留位置 | 概念 | 个人版处理方式 |
|---|---|---|---|
| API 契约 | `ChatRequest`、`KnowledgeDocumentRequest`、`KnowledgeRetrievalRequest`、`ToolExecutionApiRequest`、`ToolRegistrationRequest` | 请求体仍要求或接受 `tenantId`、`userId`、`roles`、`departments` | 新个人 API 应默认只需要 owner/agent/session/message；旧字段保留为兼容层，由 `personal` profile 自动补齐或忽略企业角色维度 |
| Console API | `ConsoleController`、`ConsoleService`、console view models | admin、ops、auditor、审计搜索、成本报表、Agent/工具/知识/Skill 管理 | 从主导航移出或改造为个人工作台配置、trace、工具/技能管理；企业角色校验只保留给旧 console 兼容入口 |
| Release API | `ReleaseController`、`ReleaseReadinessService`、`PhaseGate` | 阶段门禁、生产治理 gate、回滚动作清单 | 个人版发布验收改为功能清单和手动 smoke；旧 `/api/release/**` 可继续用于诊断，但不代表个人版完整覆盖 |
| 运行时上下文 | `RuntimeContextFactory`、`RuntimeContextScope`、`TenantStateKeyStrategy`、Agent state store | tenant/user/agent/session 复合 key | 保留 key 结构；个人版 tenant 固定映射为 `personal`，user 映射为 owner，runtimeUserId 派生为 `personal:<ownerId>`，避免重写持久化和状态恢复逻辑 |
| 持久化 schema | `ha_*` 表和 `tenant_id`、`user_id` 字段 | 租户隔离、审计、预算、知识、工具、telemetry 维度 | 表结构先不破坏；文档和查询语义改为 owner scope。后续 schema 变更必须有数据迁移计划 |
| 安全服务 | `SecurityPrincipal`、`AuthorizationService`、`DataPermissionService`、`EnterpriseIdentityService` | 企业身份、RBAC、部门/角色数据权限 | 保留脱敏、Prompt Injection guard 和最小授权能力；企业 IdP/RBAC 作为 legacy compatibility，不进入个人版默认身份模型 |
| RAG | `KnowledgeService`、`KnowledgeStore`、`JdbcKnowledgeStore`、`RetrievalPrincipal` | tenant 过滤、部门/角色/用户 ACL、RAG 指标 | 个人版改为 owner workspace 知识源和个人记忆；部门/角色 ACL 后置，引用来源和无答案策略必须保留 |
| 工具治理 | `ToolPermissionPolicy`、`ToolService`、`ToolAuditRecord`、`ToolStore` | 企业工具权限、审批人、审计策略 | 改为个人工具授权三态：允许、需要确认、拒绝。幂等、参数校验、脱敏审计继续保留 |
| Skill 治理 | `SkillGovernanceService`、Skill console views | 提议、审批、发布、禁用、回滚 | 个人版改为本地/仓库技能启用、禁用、升级、回滚和版本锁定；审批人流程不作为默认路径 |
| 前端身份 | `web/src/views/IdentityPanel.tsx`、`web/src/api/identity.ts` | 租户、角色、部门本地输入 | 个人工作台应隐藏企业身份面板，使用 owner 和 Agent 配置；旧面板可保留为开发诊断 |
| 前端视图 | `AdminWorkspace`、`OperationsWorkspace`、`ReleaseWorkspace`、导航测试 | 管理后台、运维报表、审计搜索、发布门禁 | 迁移为个人 Agent 配置、文件、知识/记忆、工具/技能、trace 和诊断视图；旧视图不再作为产品主线 |
| 文档 | `docs/start.md`、`docs/release-readiness.md`、`docs/durable-persistence-schema.md`、`docs/continue.md`、`web/README.md` | 企业平台、企业助手、运维/安全角色、发布门禁 | 已加入个人版定位说明；保留的企业内容均按 legacy compatibility 或历史边界解读 |

## 后续迁移检查点

- 新增或修改 API 时，默认不要新增企业租户、企业角色或企业部门必填项。
- 修改状态、RAG、工具、telemetry 或审计时，保留 owner/agent/session 隔离，不依赖浏览器传入的企业角色。
- 修改前端导航时，个人工作台优先；admin/ops/audit/release 入口需要明确标注为诊断或遗留。
- 修改文档时，使用“个人版目标”和“企业治理非目标/遗留兼容”措辞，不再把企业治理作为核心验收口径。
