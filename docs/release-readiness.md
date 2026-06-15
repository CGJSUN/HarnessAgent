# Enterprise Agent Platform 发布与验收手册

## MVP 业务场景

首个 MVP 业务场景选择：企业制度知识助手。

目标用户：

- 普通员工查询制度、流程和 FAQ。
- 业务专家维护知识源。
- 管理员配置 Agent、工具和权限。
- 审计员查看高风险操作和配置变更。

MVP 验收标准：

- 用户可以通过 `POST /api/chat` 和 `POST /api/chat/stream` 完成制度问答。
- RAG 启用时，回答必须带引用来源；无可访问证据时返回无答案。
- 不同租户、用户、Agent、会话之间不能共享状态、历史、知识、工具结果或审计视图。
- 高风险工具需要确认或审批，且所有拒绝、确认、执行和幂等冲突可审计。
- 控制台能够展示会话、知识、工具、Agent、Skill、指标、成本和审计视图。

## 阶段门禁

| 阶段 | 通过条件 | 回滚开关 |
|---|---|---|
| MVP Core | 聊天、会话隔离、流式响应和模型抽象通过测试 | 禁用新 Agent 或切回 echo provider |
| RAG | 文档接入、权限过滤、引用、失效和无答案通过测试 | 关闭 `knowledgeEnabled` 或禁用知识源 |
| 工具调用 | 权限拒绝、参数拒绝、确认、幂等、审计和 MCP/Agent 工具治理通过测试 | 禁用单个工具或全部高风险工具 |
| 生产运行时 | 多副本状态、租户 key、工作区、快照、遥测、限流、fallback 和超时通过测试 | 降级到单副本或切回上一配置 |
| 安全治理 | 身份、RBAC、数据权限、Prompt Injection、密钥、脱敏、审计和 Skill 生命周期通过测试 | 禁用可疑 Skill、工具、Agent 或 provider |
| 控制台 | 权限访问、配置审计、指标过滤、成本聚合和审计搜索通过测试 | 关闭管理写操作，仅保留只读视图 |
| 多 Agent | 路由、Agent-as-Tool、上下文边界、handoff、trace 和升级通过测试 | 禁用 Supervisor，回退单 Agent |

## 部署回滚流程

1. 禁用入口能力：
   - 禁用目标 Agent。
   - 禁用目标工具。
   - 关闭 RAG-backed answer。
   - 禁用 Supervisor 多 Agent 编排。

2. 切换运行依赖：
   - 切回旧模型 provider。
   - 切回上一版配置。
   - 回滚 Skill 到上一已批准发布版本。

3. 保留证据：
   - 不删除工具审计。
   - 不删除安全审计。
   - 不删除编排 trace。
   - 标记回滚原因、操作者和时间。

4. 验证恢复：
   - 重新执行租户隔离、权限过滤、高风险确认、审计搜索和健康检查。
   - 确认新请求不再进入被回滚能力。

## 端到端验收清单

- 租户隔离：
  - tenant-a 的会话、知识源、工具审计和安全审计不能被 tenant-b 看到。

- 权限过滤：
  - 普通员工不能访问管理员控制台。
  - 非审计员不能搜索审计。
  - 无部门权限用户不能看到受限知识。

- 高风险确认：
  - 高风险工具未确认时返回 `PENDING_CONFIRMATION`。
  - 确认后执行并记录审批人或确认上下文。
  - 同 idempotency key 同参数复用结果，不同参数返回冲突。

- 审计可追溯：
  - Agent Prompt 更新、工具确认、Skill 发布、编排升级都有审计记录。
  - 审计记录中的 token、邮箱、手机号和 API key 已脱敏。

- 运营可观测：
  - API、Agent、RAG、工具、Token、失败、耗时和反馈能进入指标视图。
  - 成本报表能按租户、Agent 和 provider 聚合。

## 发布前命令

```bash
openspec validate enterprise-agent-platform
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

回滚注意事项：

- 回滚服务版本前先停止新版本写流量，避免同一 schema 同时被两套模型写入。
- 回滚配置时优先切回上一版 `application-production.yml` 或环境变量。
- 不删除 `ha_security_audit`、`ha_tool_audit_records`、`ha_tool_idempotency_records`、`ha_telemetry_events`、`ha_snapshot_metadata` 和 `ha_snapshot_content`。
- 如果必须恢复数据库备份，先验证备份同时包含 session、AgentScope state、snapshot 和 idempotency 表。
