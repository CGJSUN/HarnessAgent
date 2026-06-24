## Why

项目已经明确定位为个人 Agent，但代码、API、前端和文档中仍残留 `tenant`、企业 RBAC、部门/角色、admin/ops/audit/release gate 等企业平台概念。这会持续污染产品心智、增加实现复杂度，并让后续个人 Agent 能力被旧企业兼容层牵制。

## What Changes

- **BREAKING**：个人版公共 API、前端状态、文档示例和用户可见配置不再要求或展示 `tenantId`、企业角色、企业部门、企业审批人、企业管理后台、运维报表和发布门禁。
- **BREAKING**：后端领域模型、服务命令、DTO、测试命名和包内术语从企业平台语义迁移到个人 Agent 语义，核心维度收敛为 owner、agent、session、workspace、memory、tool、skill 和 trace。
- **BREAKING**：历史 enterprise/admin/ops/release gate 入口从个人版主流程移除；确需保留的兼容入口必须被隔离为 legacy adapter 或 diagnostics，不能再作为产品能力验收路径。
- 将 `tenant` 维度从个人版领域语言中移除。若底层存储仍需要历史字段过渡，必须通过明确的数据迁移、兼容读取或一次性转换处理，不能继续让业务层围绕 tenant 建模。
- 将企业 RBAC、部门/角色数据权限和企业审批流程替换为个人授权模型：owner 作用域、个人工具确认、沙箱策略、可撤销授权和最小本地审计。
- 清理 Web 工作台导航、身份面板、API client 类型和测试，使默认界面只呈现个人 Agent 工作台能力。
- 更新 `docs/`、`web/README.md`、OpenSpec 文档和发布说明，删除企业平台主目标表述，仅在迁移记录中保留历史背景和数据迁移注意事项。
- 增加回归测试，确保新的个人 API/UI 不再把企业字段作为必填项或默认展示项，同时验证历史数据迁移后 owner/agent/session 隔离不丢失。

## Capabilities

### New Capabilities

- `personal-only-agent-contract`: 定义个人 Agent 的公共产品契约、API 语言、前端信息架构和文档术语，确保个人版只暴露 owner/agent/session/workspace 等个人语义。
- `legacy-enterprise-removal`: 定义企业概念移除、兼容入口隔离、数据迁移和验收边界，覆盖 tenant/RBAC/admin/ops/audit/release gate 等残留能力的下线规则。

### Modified Capabilities

- 无。当前仓库没有已归档的 `openspec/specs/` 主规格；本变更将以新的个人-only 能力定义后续 specs，并在设计阶段引用 `build-personal-agentscope-agent` 的已完成个人版能力作为迁移基础。

## Impact

- 影响后端 API：`api/request`、`api/response`、controller、identity resolver、chat、runtime、session、workspace、RAG、tooling、skill、orchestration、production state/telemetry/budget/snapshot 等使用 tenant/user/role/department/enterprise 命名的契约和实现。
- 影响持久化：`ha_*` 表中的 `tenant_id`、`user_id`、角色/部门 JSON、审计和 telemetry 维度需要迁移策略。破坏性 schema 修改必须提供备份、迁移、回滚和旧数据读取方案。
- 影响前端：`web/src/api`、`web/src/navigation`、`IdentityPanel`、个人工作台视图、遗留 admin/operations/release 视图和浏览器测试需要去企业化。
- 影响安全模型：企业 RBAC、企业身份服务和部门/角色数据权限不再是默认路径；保留 Prompt Injection guard、敏感信息脱敏、工具确认、沙箱和最小审计。
- 影响文档：`README.md`、`AGENTS.md`、`docs/start.md`、`docs/release-readiness.md`、`docs/durable-persistence-schema.md`、`docs/enterprise-to-personal-migration-inventory.md` 和 `web/README.md` 需要统一为个人 Agent 口径。
- 影响测试：需要新增或改造后端、前端和迁移测试，覆盖无企业字段的个人 API、个人身份默认值、owner/agent/session 隔离、legacy adapter 下线/隔离和持久化迁移安全。
