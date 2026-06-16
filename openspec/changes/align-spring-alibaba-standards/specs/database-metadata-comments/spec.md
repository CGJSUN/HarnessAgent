## ADDED Requirements

### Requirement: 历史 migration 不可改写
系统 SHALL 保持已存在的 `V1__durable_persistence.sql` 不变，并 SHALL 通过后续 migration 或 vendor-aware 脚本补充数据库表和字段注释。

#### Scenario: 补充历史表注释
- **WHEN** 需要为 V1 已创建的表和字段增加用途备注
- **THEN** 变更 SHALL 新增 V2 或更高版本 migration
- **AND** 变更 SHALL NOT 直接修改 V1 文件内容

#### Scenario: 审查 Flyway 历史
- **WHEN** 发布环境已经执行过 V1
- **THEN** 本变更 SHALL 避免导致 Flyway checksum 不一致
- **AND** 已部署环境 SHALL 能通过后续 migration 获得新的元数据注释

### Requirement: 表级注释覆盖
系统 SHALL 为当前 durable persistence schema 中的 14 张表补充表级用途注释。

#### Scenario: 检查表注释覆盖率
- **WHEN** 注释 migration 在目标数据库执行完成
- **THEN** `ha_session_messages`、`ha_security_audit`、`ha_budget_counters`、`ha_agent_state`、`ha_snapshot_metadata`、`ha_snapshot_content`、`ha_knowledge_sources`、`ha_knowledge_chunks`、`ha_rag_metrics`、`ha_rag_feedback`、`ha_tool_definitions`、`ha_tool_audit_records`、`ha_tool_idempotency_records` 和 `ha_telemetry_events` SHALL 都具备非空表级注释

#### Scenario: 阅读表级用途
- **WHEN** DBA 或开发者查看表注释
- **THEN** 注释 SHALL 能说明该表服务的业务能力、治理用途或生产运行时用途
- **AND** 注释 SHALL NOT 包含样例 prompt、工具参数、凭据或业务敏感原文

### Requirement: 字段级注释覆盖
系统 SHALL 为当前 durable persistence schema 的字段补充字段级用途注释，并 SHALL 明确隔离字段、JSON 文本字段、治理字段、RAG 字段和生产状态字段的含义。

#### Scenario: 检查字段注释覆盖率
- **WHEN** 注释 migration 在目标数据库执行完成
- **THEN** 14 张表中的现有字段 SHALL 具备非空字段注释
- **AND** 注释 SHALL 与字段实际类型、可空性和业务用途一致

#### Scenario: 检查隔离字段说明
- **WHEN** 字段为 `tenant_id`、`user_id`、`agent_id` 或 `session_id`
- **THEN** 字段注释 SHALL 说明其用于租户、用户、Agent 或会话隔离与查询定位
- **AND** 注释 SHALL NOT 暗示跨租户访问被允许

#### Scenario: 检查 JSON 文本字段说明
- **WHEN** 字段名包含 `*_json`、`attributes_json` 或 `details_json`
- **THEN** 字段注释 SHALL 说明该字段保存序列化 JSON 文本
- **AND** 注释 SHALL NOT 将其描述为数据库 native JSON 类型，除非 schema 真实改为 native JSON

#### Scenario: 检查序列化文本字段说明
- **WHEN** 字段为 `state_value`、`parameter_fingerprint` 或 `result_json`
- **THEN** 字段注释 SHALL 说明其保存运行时状态、参数指纹或工具结果的序列化文本
- **AND** 注释 SHALL 标明这些字段内容不得进入日志或文档样例

### Requirement: 治理字段注释
系统 SHALL 对工具治理、RAG 权限、审计、幂等、预算、状态和快照字段写明用途，确保字段字典能支撑排障和交接。

#### Scenario: 检查工具治理字段
- **WHEN** 字段为 `risk_level`、`mutating`、`enabled`、`approval_id`、`reviewer_id`、`idempotency_key`、`sanitized_input_json` 或 `sanitized_output_json`
- **THEN** 字段注释 SHALL 说明其在工具权限、确认/审批、幂等或审计脱敏中的用途

#### Scenario: 检查 RAG 字段
- **WHEN** 字段为 `allowed_departments_json`、`allowed_roles_json`、`allowed_users_json`、`candidate_count`、`permitted_count` 或 `failure_reason`
- **THEN** 字段注释 SHALL 说明其在知识权限过滤、检索统计或无答案原因中的用途

#### Scenario: 检查生产状态字段
- **WHEN** 字段为 `state_key`、`scope`、`state_value`、`counter_key`、`location` 或 `content`
- **THEN** 字段注释 SHALL 说明其在 AgentScope state、预算计数或工作区快照中的用途
- **AND** 注释 SHALL 标明内容字段不得被日志或文档样例泄露

### Requirement: Vendor-aware 注释迁移
系统 SHALL 提供兼容 MySQL 生产路径和 H2 本地/测试路径的注释迁移方案，避免用单一 SQL 同时假定两种数据库支持相同 COMMENT 语法。

#### Scenario: MySQL 注释迁移
- **WHEN** 目标数据库为 MySQL
- **THEN** 注释 migration SHALL 使用 MySQL 支持的表级和字段级注释语法
- **AND** 若字段注释通过 `MODIFY COLUMN` 实现，语句 SHALL 完整保留原字段类型、长度、NULL 约束和默认行为

#### Scenario: H2 注释迁移
- **WHEN** 本地或测试数据库为 H2
- **THEN** Flyway 路径 SHALL 能成功执行
- **AND** H2 路径 SHALL 使用 H2 支持的 `COMMENT ON` 语法或明确 no-op 兼容脚本

### Requirement: 注释 migration 不改变 schema 语义
数据库注释变更 SHALL 只补充元数据备注，并 SHALL NOT 改变表结构、字段类型、索引、约束、默认值、数据内容或业务查询语义。

#### Scenario: 执行注释 migration
- **WHEN** 注释 migration 执行完成
- **THEN** 现有应用读写 session、security audit、budget、agent state、snapshot、knowledge、RAG、tool 和 telemetry 表的行为 SHALL 保持不变
- **AND** migration SHALL NOT drop、rename、truncate 或重建任何已有表

#### Scenario: 对比字段定义
- **WHEN** 审查 MySQL 字段注释语句
- **THEN** 字段定义 SHALL 与 V1 中对应字段定义保持语义一致
- **AND** 唯一允许变化的元数据 SHALL 是注释内容

### Requirement: 字段字典文档同步
系统 SHALL 在项目文档中同步维护数据库字段字典，说明表用途、字段用途、JSON 字段格式、敏感内容边界和发布/回滚注意事项。

#### Scenario: 更新持久化文档
- **WHEN** 数据库注释 migration 被新增
- **THEN** `docs/start.md` 或相关持久化文档 SHALL 补充 14 张表的字段字典
- **AND** 文档 SHALL 与 migration 中的表/字段注释保持一致

#### Scenario: 更新发布文档
- **WHEN** 发布说明涉及数据库变更
- **THEN** `docs/release-readiness.md` SHALL 说明注释 migration 的验证方式和回滚策略
- **AND** 文档 SHALL 明确 V1 不可改写、后续修正通过 roll-forward migration 完成

### Requirement: 元数据验证
系统 SHALL 提供可执行或可人工复核的验证方式，确认数据库表/字段注释覆盖率和敏感内容约束。

#### Scenario: MySQL 元数据验收
- **WHEN** 注释 migration 在 MySQL 环境执行完成
- **THEN** 验收 SHALL 查询 `information_schema.tables` 和 `information_schema.columns`
- **AND** 验收 SHALL 确认 14 张表和现有字段具备非空注释

#### Scenario: H2 兼容验收
- **WHEN** 本地或测试环境执行 Flyway migration
- **THEN** H2 SHALL 能执行完整迁移路径或明确的 H2 兼容路径
- **AND** 现有直接加载 V1 的 JDBC store 测试 SHALL 不因新增注释 migration 被破坏

#### Scenario: 手写 H2 DDL 兼容验收
- **WHEN** 测试调用 `JdbcStoreTestSupport` 创建局部 knowledge、tool 或 telemetry 表
- **THEN** 手写 H2 DDL SHALL 继续可执行
- **AND** 实现 SHALL NOT 强制把 MySQL 注释语法复制到这些测试 DDL 中

#### Scenario: 敏感内容验收
- **WHEN** 审查表注释、字段注释和字段字典
- **THEN** 注释 SHALL NOT 包含原始 prompt、query、工具输入输出、token、password、DSN、Redis URI、snapshot 内容或 workspace 文件内容

### Requirement: Roll-forward 回滚策略
数据库注释 migration 发布后 SHALL 通过后续 migration 修正或清理注释，并 SHALL NOT 删除、改写已发布 migration 或破坏业务数据。

#### Scenario: 已发布注释需要修正
- **WHEN** 已执行的注释内容存在错误或需要调整
- **THEN** 团队 SHALL 新增 V3 或更高版本 migration 修正注释
- **AND** 团队 SHALL NOT 修改已发布 V2 migration

#### Scenario: 注释 migration 半失败
- **WHEN** 数据库注释 migration 在某个环境半失败
- **THEN** 团队 SHALL 先记录 Flyway 状态和实际 metadata 差异
- **AND** 团队 SHALL 按数据库变更流程处理，必要时经 DBA 审批后执行 repair
- **AND** 团队 SHALL NOT 通过 drop 表、清数据或改写历史 migration 处理注释问题
