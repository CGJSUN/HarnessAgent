# HarnessAgent 个人版发布与验收手册

## MVP 业务场景

当前产品目标是个人版完整 Agent。首个个人版验收场景选择：个人知识和工作区助手。

目标用户：

- 个人 owner 通过聊天和工作区完成资料整理、文件处理、知识问答和任务规划。
- 个人 owner 管理本地知识源、记忆、工具、技能和 Agent 配置。
- 维护者检查本地/生产配置、持久化、沙箱、trace、最小审计和回滚能力。

MVP 验收标准：

- 用户可以通过 `POST /api/chat` 和 `POST /api/chat/stream` 完成制度问答。
- RAG 启用时，回答必须带引用来源；无可访问证据时返回无答案。
- 不同 owner、Agent、会话和工作区之间不能共享状态、历史、知识、记忆、工具授权或 trace。
- 高风险工具、技能、代码、Shell、SQL、外部写操作和网络副作用需要个人确认或沙箱策略。
- 工作台能够展示聊天、计划、工作区文件、知识/记忆、工具确认、技能、Agent 配置和 trace。
- 早期企业租户、企业 RBAC、运营报表、审计报表和发布门禁只作为遗留兼容或诊断，不作为个人版主验收目标。

## 个人版阶段验收

| 阶段 | 通过条件 | 回滚开关 |
|---|---|---|
| Personal Core | owner/agent/session 隔离、非流式/流式聊天、模型抽象、预算、超时和 fallback 通过测试 | 禁用新个人 Agent 或切回 echo provider |
| Workspace | 工作区初始化、路径拒绝、文件引用、快照恢复、计划模式、channel 和压缩通过测试 | 禁用工作区写操作或切回上一 workspace backend |
| Memory/RAG | 个人知识源、记忆、索引状态、引用来源、无答案、删除和导出通过测试 | 关闭 RAG provider 或禁用目标知识源 |
| Tool/HITL | 参数拒绝、只读允许、写操作确认、HITL 恢复、结构化结果、幂等和最小审计通过测试 | 禁用单个工具或全部高风险工具 |
| Skill | 本地技能仓库、元数据、加载、权限、启停、升级、回滚和验证通过测试 | 禁用目标 Skill 或锁定上一版本 |
| Multi-Agent | 子 Agent 规格、路由、后台委派、Agent-as-Tool、上下文边界、trace 和失败降级通过测试 | 禁用 Supervisor，回退单 Agent |
| Workbench | 聊天、计划、文件、知识/记忆、工具、技能、配置、trace 在桌面和移动视口通过测试 | 隐藏新工作台模块或回退旧 console |

## 遗留 release gate 处理

`/api/release/scenario` 和 `/api/release/phase-gates` 仍可用于检查早期企业 MVP 和生产运行时 wiring，但它们不是个人版完整 Agent 的唯一验收依据。个人版发布必须同时参考 [AgentScope Java v2 覆盖矩阵](agentscope-java-v2-coverage.md) 和 [企业到个人版迁移盘点](enterprise-to-personal-migration-inventory.md)。

## 部署回滚流程

1. 禁用入口能力：
   - 禁用目标 Agent。
   - 禁用目标工具。
   - 关闭 RAG-backed answer。
   - 禁用 Supervisor 多 Agent 编排。

2. 切换运行依赖：
   - 切回旧模型 provider。
   - 切回上一版配置。
   - 按发布预案回滚 Skill 到上一已批准发布版本；当前控制台未开放可执行 rollback REST/UI。

3. 保留证据：
   - 不删除工具最小审计。
   - 不删除安全最小审计。
   - 不删除编排 trace。
   - 标记回滚原因、操作者和时间。

4. 验证恢复：
   - 重新执行 owner/Agent/session 隔离、工作区路径拒绝、高风险确认、trace 和健康检查。
   - 确认新请求不再进入被回滚能力。

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

- 最小审计可追溯：
  - Agent 配置更新、工具确认、Skill 版本变更、多 Agent 委派都有脱敏记录。
  - 审计记录中的 token、邮箱、手机号、API key、prompt、工具原始结果和文件内容不会明文落库。

- Trace 可观测：
  - API、Agent、RAG、工具、子 Agent、Token、失败、耗时和反馈能进入个人 trace 或诊断视图。
  - 用量能按 owner、Agent 和 provider 聚合；租户成本报表只作为遗留兼容。

## 发布前命令

```bash
openspec validate build-personal-agentscope-agent
mvn test
```

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
curl -i http://localhost:8080/api/release/scenario
```

Durable persistence 发布前检查：

```bash
curl -s http://localhost:8080/api/release/phase-gates
```

检查返回中 `Production Runtime` gate：

- `status` 必须为 `PASSED`。
- `checks` 应包含 `distributed-state`、`workspace`、`telemetry`、`budget`、`timeout`、`snapshot`。
- `failureReasons` 必须为空。

DB schema and metadata comment migration 验收：

- Flyway locations 必须包含 `classpath:db/migration,classpath:db/vendor-migration/{vendor}`。
- H2 路径由 `DurablePersistenceMigrationTest` 覆盖，确认公共 V1/V3/V5/V7/V9 与 H2 V2/V4/V6/V8/V10 可执行，并能读取 16 张表和 168 个字段 comments。
- MySQL 发布前需在目标 schema 执行 [durable-persistence-schema.md](durable-persistence-schema.md) 中的 `information_schema.tables` / `information_schema.columns` 查询，确认 16 张表均有 `table_comment`，且字段 comment 缺失查询无返回。
- MySQL vendor comment migration 使用 `MODIFY COLUMN ... COMMENT`，发布前需由 DBA 或 schema owner 审核字段类型、NULL 约束和 `AUTO_INCREMENT` 语义未被改变。
- V2/V4/V6/V8/V10 只增加 metadata comments。回滚优先 roll-forward 到后续 vendor comment migration；除非已有数据库备份和停写窗口，不通过删除 durable 表、回退 V1/V3/V5/V7/V9 DDL 来处理 comment 文案问题。

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

回滚注意事项：

- 回滚服务版本前先停止新版本写流量，避免同一 schema 同时被两套模型写入。
- 回滚配置时优先切回上一版 `application-production.yml` 或环境变量。
- 不删除 `ha_security_audit`、`ha_personal_memories`、`ha_knowledge_sources`、`ha_knowledge_chunks`、`ha_tool_audit_records`、`ha_tool_idempotency_records`、`ha_tool_pending_confirmations`、`ha_telemetry_events`、`ha_snapshot_metadata` 和 `ha_snapshot_content`。
- 如果必须恢复数据库备份，先验证备份同时包含 session、AgentScope state、snapshot 和 idempotency 表。
