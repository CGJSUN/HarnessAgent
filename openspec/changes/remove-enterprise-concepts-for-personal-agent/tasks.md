## 1. Inventory And Guardrails

- [x] 1.1 扫描后端、前端、文档、测试和迁移脚本中的企业术语，生成可执行的残留清单，按 public API、domain/service、persistence、frontend、docs、tests 分类。
- [x] 1.2 定义企业术语门禁脚本和 allowlist，默认检查个人主路径中的 tenant、RBAC、role、department、admin、ops、release gate、enterprise console 等术语。
- [x] 1.3 将 migration、legacy adapter、archive、数据库迁移 SQL 和历史说明加入显式 allowlist，并为每个例外记录保留原因。
- [x] 1.4 将术语门禁接入发布前检查，确保未允许的企业术语回流会失败。

## 2. Personal Identity And API Contract

- [x] 2.1 新增 owner scope / owner identity 模型，替换业务层中的 tenant、role、department 默认身份语义。
- [x] 2.2 将聊天、会话、工作区、知识/记忆、工具、技能、Agent 配置和 trace API 的 request/response 迁移为 owner、agent、session、workspace 个人契约。
- [x] 2.3 移除个人版公共 API 对 `tenantId`、`roles`、`departments` 的必填和文档化要求。
- [x] 2.4 如需保留旧请求格式，新增默认关闭的 legacy adapter，并禁止个人工作台默认调用该 adapter。
- [x] 2.5 更新 API 契约测试，覆盖无企业字段的个人请求、owner/agent/session 隔离和 legacy adapter 隔离。

## 3. Domain And Service Cleanup

- [x] 3.1 将 RuntimeContext、identity resolver、RAG principal、tool principal、skill owner 和 telemetry command 等服务接口迁移到 owner 语义。
- [x] 3.2 重命名或替换 `TenantStateKeyStrategy`、`SecurityPrincipal`、`RetrievalPrincipal` 等企业命名类型，使主代码路径表达个人 Agent 语义。
- [x] 3.3 更新日志字段和 reason code，使用 owner hash、agent、session hash、resource 和状态字段，避免继续输出 tenant 语义。
- [x] 3.4 清理后端测试夹具和测试名称中的企业术语，保留必要 legacy/migration 测试例外。

## 4. Persistence And State Migration

- [x] 4.1 设计并实现 Flyway migration，将历史 `tenant_id` / `user_id` 语义迁移到 owner scope，保留会话、记忆、知识源、工具确认、幂等、trace、telemetry、snapshot 和 Agent state。
- [x] 4.2 移除或替换角色/部门 ACL 字段，使用 owner、agent、source visibility 和个人授权记录表达可见性。
- [x] 4.3 迁移 JDBC、local JSON、Redis 和 AgentScope state key，从 `tenant:<...>` 形态切换到 `owner:<...>` 形态。
- [x] 4.4 增加旧 key 读取和一次性迁移测试，确认迁移后后续写入只使用 owner key。
- [x] 4.5 更新 durable persistence 文档，说明新 owner schema、备份要求、反向迁移或恢复策略。

## 5. Personal Authorization, RAG, Tools, And Skills

- [x] 5.1 删除默认企业 RBAC、部门 ACL、企业身份提供方和企业审批人流程在个人主路径中的依赖。
- [x] 5.2 实现个人授权模型，覆盖 owner 默认管理权、allow/confirm/deny 工具授权、高风险确认、沙箱、幂等和最小本地审计。
- [x] 5.3 将 RAG 检索权限迁移为 owner、agent、source visibility 和个人授权，不再依赖角色、部门或企业用户列表。
- [x] 5.4 将 Skill 启用、禁用、升级、回滚和锁定迁移为个人授权与本地审计，不再要求企业审批或企业发布流程。
- [x] 5.5 增加工具、RAG 和 Skill 测试，覆盖低风险直接执行、高风险确认、个人可见性、禁用/回滚和无企业权限查询。

## 6. Frontend Personal Workbench

- [x] 6.1 移除默认 IdentityPanel 中的租户、角色和部门输入，工作台身份只展示 owner 和当前 Agent。
- [x] 6.2 清理导航和信息架构，默认仅展示聊天、计划、文件、知识/记忆、工具、技能、Agent 配置和 trace/diagnostics。
- [x] 6.3 移除或隔离 Admin、Operations、Release 等企业视图；若保留诊断入口，移动到明确 diagnostics/legacy 路径并默认隐藏。
- [x] 6.4 更新 web API types、client、mock 数据和组件文案，避免个人主界面暴露企业租户、企业角色、企业部门或企业发布门禁。
- [x] 6.5 更新前端单元测试和 Playwright 测试，覆盖默认 UI 不展示企业身份或企业导航。

## 7. Documentation And Migration Notes

- [x] 7.1 更新 README、AGENTS.md、docs/start.md、docs/release-readiness.md、docs/durable-persistence-schema.md 和 web/README.md 为个人 Agent 口径。
- [x] 7.2 将企业历史说明收敛到 legacy/migration/archive 语境，明确其不是个人版默认产品路径。
- [x] 7.3 更新发布与回滚文档，说明 schema/state/API breaking change 的备份、验证、回滚和旧服务兼容限制。
- [x] 7.4 更新 OpenSpec 相关说明，确保个人-only 契约和 legacy 企业移除边界一致。

## 8. Verification And Release Readiness

- [x] 8.1 运行企业术语门禁，确认个人主路径无未允许的企业概念残留。
- [x] 8.2 运行 OpenSpec 校验，确认 proposal、design、specs 和 tasks 格式有效。
- [x] 8.3 运行后端测试，覆盖个人 API、owner 隔离、持久化迁移、旧 key 迁移、RAG、工具、Skill、trace 和回滚边界。
- [x] 8.4 运行前端 `npm run test:unit`、`npm run test:browser` 和 `npm run build`。
- [x] 8.5 完成迁移 smoke 验收，覆盖旧数据备份、schema/state 迁移、个人工作台启动、个人聊天、工作区、记忆/RAG、工具确认、Skill 和 trace。
- [x] 8.6 更新任务状态和发布记录，说明哪些 legacy adapter 或 diagnostics 入口仍短期保留以及删除计划。
