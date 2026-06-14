# HarnessAgent 企业 Agent 平台学习、部署与使用指南

本文面向三类读者：

- 业务和产品同学：理解平台能力边界、分阶段验收点和高风险操作治理。
- 后端和平台工程师：学习 Spring Boot、AgentScope Java v2、HarnessAgent、RAG、工具治理和生产运行时的实现路径。
- 运维和安全同学：掌握部署配置、状态存储、权限、审计、限流、回滚和故障排查方式。

建议按章节学习。新同学先读第 1 到第 4 章即可跑通本地 MVP；负责生产化的人继续读第 7 到第 11 章；负责治理和平台化的人重点读第 5、6、8、9、10 章。

## 1. 平台目标和边界

HarnessAgent 的目标是用 AgentScope Java v2 和 HarnessAgent 建设一个可工程化落地的企业 Agent 平台，而不是临时拼接聊天 SDK。

第一阶段先交付一个可靠的企业助手闭环：

- Spring Boot + Maven + JDK 17 项目。
- `POST /api/chat` 非流式聊天。
- `POST /api/chat/stream` 流式聊天。
- 租户、用户、Agent、会话隔离。
- 模型提供方抽象。
- 会话和消息管理。

随后逐步扩展：

- 企业知识库 RAG。
- 受治理的工具调用。
- 生产运行时。
- 安全治理。
- 管理和运营控制台。
- 多 Agent 编排。

平台默认不允许 Agent 绕过权限直接读取知识、调用工具、发送消息、提交审批、改写业务系统或执行不可信代码。所有高风险能力必须经过权限、参数、确认或审批、幂等和审计控制。

## 2. 总体架构

当前平台按以下层次组织：

```text
用户入口
  Web / 企业 IM / API 网关 / 内部系统
        |
        v
Spring Boot API 层
  chat / sessions / knowledge / tools
        |
        v
平台服务层
  Agent Core / RAG / Tool Governance / Production Runtime
        |
        v
HarnessAgent + AgentScope Java v2
  ModelProvider / RuntimeContext / Session / Workspace
        |
        v
基础设施
  Redis 或 MySQL / Remote FS 或 Sandbox / Snapshot / Telemetry
```

主要代码目录：

- `src/main/java/com/harnessagent/agent`：HarnessAgent 和 AgentScope runtime 适配。
- `src/main/java/com/harnessagent/chat`：聊天编排、RAG 注入、预算和遥测入口。
- `src/main/java/com/harnessagent/rag`：知识源、切片、检索、引用和反馈。
- `src/main/java/com/harnessagent/tooling`：工具注册、执行、权限、参数、确认、幂等和审计。
- `src/main/java/com/harnessagent/production`：生产运行时配置、状态 key、工作区、遥测、预算、fallback 和超时策略。
- `src/main/java/com/harnessagent/api`：HTTP API 控制器和请求响应模型。

## 3. 本地开发环境

必需环境：

- JDK 17 或更高版本。
- Maven 3.9 或兼容版本。
- 可选：真实模型提供方 API key，例如 `DASHSCOPE_API_KEY`。

检查 Java：

```bash
java -version
mvn -version
```

如果 `mvn test` 或 `mvn spring-boot:run` 报 `无效的标记: --release`、`class file version 61.0` 或 Spring Boot Maven 插件无法加载，说明当前 shell 使用的是 Java 8。需要安装 JDK 17，并确认 `mvn -version` 里的 Java version 也是 17。

macOS 已注册 JDK 17 时：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

如果 Homebrew 已安装 `openjdk@17`，但 `/usr/libexec/java_home -v 17` 找不到，可以使用 Homebrew 路径：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

本地启动：

```bash
mvn spring-boot:run
```

离线或只做后端启动 smoke test 时，可以跳过测试编译：

```bash
mvn -o -Dmaven.test.skip=true spring-boot:run
```

启动成功后验证：

```bash
curl -i http://localhost:8080/api/release/scenario
```

默认端口是 `8080`。基础配置在 `src/main/resources/application.yml`。

## 4. MVP 聊天能力

聊天请求必须携带：

- `tenantId`
- `userId`
- `agentId`
- `sessionId`
- `message`

非流式聊天示例：

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "userId": "user-a",
    "agentId": "enterprise-assistant",
    "sessionId": "session-a",
    "message": "帮我总结一下企业助手能做什么"
  }'
```

流式聊天：

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "userId": "user-a",
    "agentId": "enterprise-assistant",
    "sessionId": "session-a",
    "message": "请用三句话介绍平台能力"
  }'
```

会话相关 API：

- `GET /api/sessions?tenantId=tenant-a&userId=user-a&agentId=enterprise-assistant`
- `GET /api/sessions/{sessionId}/messages?tenantId=tenant-a&userId=user-a&agentId=enterprise-assistant`
- `DELETE /api/sessions/{sessionId}?tenantId=tenant-a&userId=user-a&agentId=enterprise-assistant`

运行时隔离规则：

```text
RuntimeContext.userId    = tenantId + ":" + userId
RuntimeContext.sessionId = agentId + ":" + sessionId
生产状态 key = tenant + user + agent + session + isolationScope
```

## 5. 知识库 RAG

RAG 目标不是简单向量检索，而是受权限治理的企业知识访问。

知识源注册示例：

```bash
curl -X POST http://localhost:8080/api/knowledge/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "ownerId": "owner-a",
    "title": "报销制度",
    "version": "v1",
    "visibility": "PUBLIC",
    "updatePolicy": "manual",
    "content": "发票需要在三十天内提交，超过期限需要主管审批。"
  }'
```

检索示例：

```bash
curl -X POST http://localhost:8080/api/knowledge/retrieve \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "userId": "user-a",
    "departments": [],
    "roles": [],
    "query": "发票多久提交",
    "limit": 3
  }'
```

聊天时启用 RAG：

```json
{
  "tenantId": "tenant-a",
  "userId": "user-a",
  "agentId": "enterprise-assistant",
  "sessionId": "session-rag",
  "message": "发票多久提交？",
  "knowledgeEnabled": true,
  "knowledgeLimit": 3
}
```

RAG 行为要求：

- 检索前按租户过滤。
- 检索后按部门、角色、用户和知识源权限过滤。
- 只有可访问证据会进入 Agent prompt 和引用来源。
- 证据不足时返回无答案，不让模型编造带来源回答。
- 知识源删除、撤销或版本失效后，旧切片不能继续被引用。

## 6. 受治理的工具调用

工具治理覆盖以下环节：

- 工具注册表：schema、归属系统、归属人、风险等级、权限策略、审计策略。
- 风险分级：只读工具和高风险工具。
- 权限检查：tenant、user、agent、department、role。
- 参数校验：必填字段、白名单字段、枚举值约束。
- 高风险确认：用户确认或审核人审批。
- 幂等：会修改外部系统的工具必须提供 idempotency key。
- 审计：记录脱敏输入、脱敏输出、耗时、状态、审批人和失败原因。
- MCP：MCP-backed 工具也必须走同一治理链路。

注册只读工具示例：

```bash
curl -X POST http://localhost:8080/api/tools \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "name": "crm.customer.lookup",
    "description": "按客户编号查询客户信息",
    "ownerSystem": "CRM",
    "ownerId": "owner-a",
    "sourceType": "INTERNAL",
    "sourceRef": "crm",
    "riskLevel": "READ_ONLY",
    "mutating": false,
    "requiredParameters": ["customerId"],
    "optionalParameters": ["token"],
    "sensitiveParameters": ["token"],
    "sensitiveResultFields": ["email"],
    "allowedUsers": ["user-a"],
    "allowedAgents": ["enterprise-assistant"]
  }'
```

执行工具示例：

```bash
curl -X POST http://localhost:8080/api/tools/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "userId": "user-a",
    "agentId": "enterprise-assistant",
    "sessionId": "session-a",
    "toolId": "<tool-id>",
    "parameters": {
      "customerId": "C-1001",
      "token": "secret"
    }
  }'
```

高风险工具必须先返回 `PENDING_CONFIRMATION`，用户确认或审核通过后才能执行。变更类工具还必须带 `idempotencyKey`，同一个 key 和同一组参数会复用已有结果，同一个 key 但参数不同会返回幂等冲突。

审计查询：

```bash
curl 'http://localhost:8080/api/tools/audit?tenantId=tenant-a'
```

## 7. 生产运行时

生产运行时要解决几个问题：

- 多副本状态共享。
- 租户感知状态 key。
- 远程工作区和沙箱工作区。
- 沙箱快照。
- API、Agent、模型、RAG、工具、Token、耗时、异常、反馈遥测。
- 按租户、用户、Agent、模型提供方限流和预算。
- 模型 provider fallback。
- 流式响应、工具和沙箱超时。

关键配置：

```yaml
harness-agent:
  production:
    profile: production
    replica-count: 2
    state-store:
      type: redis
      redis-uri: redis://redis:6379/0
    remote-filesystem:
      root-uri: s3://harness-agent-workspaces
    sandbox:
      enabled: true
      image: harness-agent-sandbox:latest
      workspace-root: /workspace
    snapshot-store:
      type: s3
      uri: s3://harness-agent-snapshots
    observability:
      enabled: true
      service-name: harness-agent
    budget:
      request-limit: 1000
      token-limit: 1000000
    fallback:
      retryable-status-codes: [429, 500, 502, 503, 504]
      providers:
        dashscope: [echo]
    timeouts:
      model-timeout: 2m
      stream-timeout: 5m
      tool-timeout: 30s
      sandbox-timeout: 10m
```

生产约束：

- `profile=production` 且 `replica-count > 1` 时，不能使用 `local-json` 状态。
- 普通知识或办公类 Agent 使用远程工作区。
- code、shell、SQL、不可信执行类 Agent 必须使用沙箱。
- 沙箱在生产环境必须配置 OSS、S3、MinIO 或 JDBC 快照存储。
- 超预算请求会在进入模型前被拒绝。
- 只有可重试模型失败才触发 fallback；权限、预算、参数错误不会 fallback。

## 8. 安全治理

安全治理原则：

- 身份接入企业 IdP，例如 SSO、OAuth2、LDAP、飞书、钉钉、企微或内部账号。
- 对 Agent、工具、知识源、管理操作和运营数据执行 RBAC。
- RAG、工具、记忆、文件、审计视图使用一致的数据权限。
- 防 Prompt Injection，不允许检索内容或用户输入扩大工具和数据权限。
- 模型 key、企业凭证和工具 token 存储在配置或密钥存储中，不能硬编码。
- 日志、trace、审计和诊断信息必须脱敏。
- 高风险操作必须保留可搜索审计记录。

当前代码里的安全治理入口：

- `EnterpriseIdentityService`：接收企业身份断言，支持 SSO、OAuth2、LDAP、飞书、钉钉、企微和内部身份源类型。
- `AuthorizationService`：按 tenant、user、role、department、permission 执行 RBAC。
- `DataPermissionService`：对知识源、工具输出、记忆、文件和审计资源执行一致的数据权限过滤。
- `PromptInjectionGuard`：在聊天和工具参数进入下游前拦截绕过权限、泄露密钥、忽略策略等危险指令。
- `SecretStore` / `EnvironmentSecretStore`：通过 `env:<NAME>` 等引用解析模型 key 和企业凭证。
- `SensitiveDataRedactor`：对邮箱、手机号、token、password、apiKey、credential 等字段脱敏。
- `SecurityAuditService`：留存高风险操作、审批、确认和人工干预审计记录，并通过审计权限控制搜索。
- `SkillGovernanceService`：管理 Skill 提议、审批、发布、禁用和回滚。

### 身份接入契约

本地开发可以在请求体中传 `tenantId`、`userId`、`roles`、`departments`。生产环境应由 API 网关或认证中间件注入权威身份头，服务端以认证头为准：

| Header | 含义 |
|---|---|
| `X-Tenant-Id` | 权威租户标识 |
| `X-User-Id` | 权威用户标识 |
| `X-Identity-Provider` | `sso`、`oauth2`、`ldap`、`lark`、`dingtalk`、`wechat_work`、`internal` |
| `X-Roles` | 逗号分隔角色，例如 `employee,auditor` |
| `X-Departments` | 逗号分隔部门，例如 `finance,shared-service` |

如果认证头存在，请求体中的 `tenantId` 或 `userId` 与认证头不一致，平台会拒绝请求。生产环境不要信任客户端自己传入的部门和角色。

### RBAC 权限矩阵

| 资源 | 普通员工 | 业务专家 | 审核人 | 管理员 | 审计员 |
|---|---|---|---|---|---|
| Agent 聊天 | 读/执行已授权 Agent | 读/执行已授权 Agent | 读/执行已授权 Agent | 管理 Agent | 按权限查看记录 |
| 知识源 | 读取可见知识 | 注册和维护授权知识 | 复核敏感知识 | 管理全部知识配置 | 查看知识访问审计 |
| 工具 | 执行已授权只读工具 | 维护业务工具元数据 | 审批高风险工具 | 注册、禁用、授权工具 | 搜索工具审计 |
| 管理操作 | 无 | 有限维护 | 审批 | 管理 | 只读审计 |
| 运营数据 | 自己范围 | 业务范围 | 审批范围 | 全局运营 | 审计范围 |
| Skill | 使用已发布版本 | 提议版本 | 审核版本 | 发布/禁用/回滚 | 查看生命周期审计 |

### 统一数据权限模型

RAG、工具输出、会话记忆、文件和审计搜索都应使用同一组权限维度：

- `tenantId`
- `userId`
- `roles`
- `departments`
- `resourceType`
- `permission`

跨租户数据永远不可见。部门、角色或用户级授权不满足时，数据不能进入 Agent prompt、工具结果、会话历史、文件下载或审计搜索结果。

### Prompt Injection 与工具安全

检索内容和用户输入都只能作为数据处理，不能改变平台策略。遇到以下内容应拒绝、降级或进入人工复核：

- 要求忽略系统提示词或平台策略。
- 要求泄露 API key、token、凭证或系统提示词。
- 要求调用未授权工具。
- 要求扩大租户、部门、角色或用户权限。
- 在工具参数中注入白名单外字段。

工具调用仍必须经过 `ToolService` 的权限、参数、确认、幂等和审计链路。

### 密钥管理

模型 key、企业系统凭证和工具 token 必须通过批准的 secret 来源解析，例如：

- `env:DASHSCOPE_API_KEY`
- Kubernetes Secret 注入的环境变量。
- 企业 Vault 或内部密钥服务。

生产环境禁止把真实 key 写入 `application.yml`、审计记录、trace attributes、异常消息或诊断导出。

### 脱敏和诊断导出

默认脱敏字段包括：

- `token`
- `password`
- `secret`
- `apiKey`
- `api_key`
- `authorization`
- `credential`

文本中常见邮箱和手机号也会被脱敏。日志、trace、工具审计、安全审计和诊断导出都应复用同一套脱敏策略。

### 高风险审计

高风险审计记录应覆盖：

- 高风险工具确认或审批。
- 审批提交。
- 模型响应导致的安全拦截。
- Prompt Injection 拒绝。
- 人工干预。
- Skill 发布、禁用和回滚。

审计搜索必须要求 `SEARCH_AUDIT` 权限，审计记录不能被业务回滚删除。

### Skill 治理生命周期

生产 Skill 必须走以下状态：

```text
PROPOSED -> APPROVED -> PUBLISHED
                         |
                         v
                      DISABLED
                         |
                         v
                    ROLLED_BACK
```

Agent 只能使用已批准且已发布的 Skill 版本。禁用后立即不可用，回滚时恢复目标已批准版本并保留审计。

生产审计至少包含：

- 操作者。
- 租户、用户、Agent、会话。
- 工具名称和风险等级。
- 脱敏参数和脱敏结果。
- 确认人或审批人。
- 状态、耗时、失败原因。

安全治理学习建议：

1. 先阅读身份和 RBAC：确认企业身份如何映射成 `SecurityPrincipal`。
2. 再阅读数据权限：确认同一个用户通过 RAG、工具、记忆、文件和审计视图看到的数据一致。
3. 然后阅读 Prompt/工具安全：确认模型输出、检索内容和用户输入都不能扩大权限。
4. 最后阅读密钥、脱敏、审计和 Skill 治理：确认生产环境不会把凭证和敏感数据写进日志或 trace。

安全验收清单：

- 未认证请求在生产网关层返回 401。
- 已认证但缺少资源权限的请求返回 403 或受控拒绝。
- 请求体伪造 `tenantId`、`userId`、`roles` 或 `departments` 时不会覆盖认证头。
- RAG、工具、记忆、文件和审计视图不会暴露越权数据。
- Prompt Injection 会被拒绝或降级。
- 密钥只通过批准的 Secret 来源解析。
- 日志、trace、审计和诊断导出无明文 token、API key、邮箱或手机号。
- 高风险工具、审批、人工干预和 Skill 生命周期有审计记录。

## 9. 管理和运营控制台

控制台分三类视图：

- 用户端：聊天、流式输出、历史、引用来源、文件上传、工具状态、确认提示。
- 管理端：Agent、Prompt、模型、工作区 profile、工具、知识库、Skill、权限。
- 运营端：会话、模型调用、工具调用、RAG 命中、失败、耗时、Token 成本、反馈和审计搜索。

控制台实现时不要把页面做成宣传页。第一屏应直接提供可用工作台：会话列表、聊天区、引用来源、工具确认和状态信息。

当前后端提供 `/api/console/**` 聚合接口，供 Web 控制台或企业 IM 工作台使用：

| API | 用途 | 角色 |
|---|---|---|
| `GET /api/console/user` | 用户聊天控制台：会话、消息、引用、工具状态、确认提示 | 已授权用户 |
| `GET /api/console/agents` | Agent 列表和配置视图 | admin |
| `PATCH /api/console/agents/{agentId}/prompt` | 更新 Agent Prompt 并记录审计 | admin |
| `GET /api/console/tools` | 工具注册、分类、启用和权限视图 | admin |
| `GET /api/console/knowledge` | 知识源版本、权限、索引状态和删除视图 | admin |
| `GET /api/console/skills` | Skill 仓库、版本、审批状态、禁用和回滚视图 | admin |
| `GET /api/console/metrics` | 会话、模型、工具、RAG、失败、耗时和反馈指标 | admin 或 ops |
| `GET /api/console/cost` | 按租户、Agent、模型提供方聚合的成本和用量 | admin 或 ops |
| `GET /api/console/audit` | 会话、配置变更、工具调用、审批和人工干预审计搜索 | auditor 或 admin |

控制台 API 同样使用身份头：

```bash
curl 'http://localhost:8080/api/console/metrics?tenantId=tenant-a&userId=ops-a' \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'X-User-Id: ops-a' \
  -H 'X-Roles: ops'
```

更新 Agent Prompt 示例：

```bash
curl -X PATCH 'http://localhost:8080/api/console/agents/enterprise-assistant/prompt?tenantId=tenant-a&userId=admin-a' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'X-User-Id: admin-a' \
  -H 'X-Roles: admin' \
  -d '{"systemPrompt":"你是企业助手，回答必须遵守权限、引用和工具治理要求。"}'
```

控制台验收要点：

- 非管理员不能访问 Agent、工具、知识库和 Skill 管理视图。
- 非 ops 或 admin 不能访问运营指标和成本报表。
- 非 auditor 或 admin 不能搜索审计。
- Prompt 更新、Skill 生命周期、高风险工具确认和人工干预必须进入审计。
- 指标和成本报表不能包含原始 prompt、token、凭证或敏感业务字段。

## 10. 多 Agent 编排

多 Agent 阶段建议在核心、RAG、工具、生产运行时和安全治理稳定后再做。

专家 Agent 示例：

- 知识库 Agent。
- 数据分析 Agent。
- 审批 Agent。
- 客服 Agent。
- 运维 Agent。
- 代码 Agent。

Supervisor 负责：

- 理解任务意图。
- 判断用户权限。
- 选择专家 Agent。
- 规划步骤。
- 记录 routing、handoff、工具调用和最终回答组装 trace。

跨 Agent 共享上下文时必须遵守权限边界，只传递子 Agent 契约和用户权限允许的数据。

当前后端提供 `/api/orchestration/**` 编排接口：

| API | 用途 |
|---|---|
| `POST /api/orchestration/agents` | 注册专家 Agent，保存用途、契约、权限、工具、知识访问和归属人 |
| `POST /api/orchestration/route` | Supervisor 根据任务意图、权限和 Agent 能力路由 |
| `GET /api/orchestration/agents/{agentId}/tool` | 将已批准子 Agent 暴露为 Agent-as-Tool |
| `GET /api/orchestration/traces` | 查询 Supervisor 决策、子 Agent 调用、handoff 和升级 trace |

注册专家 Agent 示例：

```bash
curl -X POST http://localhost:8080/api/orchestration/agents \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "name": "knowledge-agent",
    "purpose": "回答制度知识问题",
    "inputContract": "text question",
    "outputContract": "grounded answer with citations",
    "requiredRoles": ["employee"],
    "allowedTools": ["crm.customer.lookup"],
    "allowedKnowledgeSources": ["policy-handbook"],
    "ownerId": "owner-a",
    "approved": true
  }'
```

Supervisor 路由示例：

```bash
curl -X POST http://localhost:8080/api/orchestration/route \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-a' \
  -H 'X-User-Id: user-a' \
  -H 'X-Roles: employee' \
  -d '{
    "tenantId": "tenant-a",
    "userId": "user-a",
    "supervisorAgentId": "supervisor",
    "taskIntent": "制度知识",
    "task": "查询报销制度",
    "context": {
      "question": "发票多久提交？",
      "citations": ["policy-handbook:v1"],
      "secret": "不应共享"
    }
  }'
```

上下文边界：

- 默认不共享长期记忆。
- 默认不共享文件。
- 默认不共享原始工具输出。
- 只共享契约允许的检索知识、引用和问题摘要。
- 低置信度、无权限或策略阻止时返回 `ESCALATED`，由人工澄清、审批或停止。

编排 trace 至少包含：

- Supervisor Agent。
- 候选专家 Agent。
- 选中 Agent。
- 路由置信度。
- 步骤执行记录。
- handoff 记录。
- 失败或升级原因。

## 11. 部署流程

本地开发：

```bash
mvn test
mvn spring-boot:run
```

打包：

```bash
mvn clean package
```

运行：

```bash
java -jar target/harness-agent-0.1.0-SNAPSHOT.jar
```

生产部署前检查：

- JDK 17 已安装。
- `harness-agent.production.profile=production`。
- 多副本时 `state-store.type` 是 `redis` 或 `mysql`。
- 模型 provider API key 来自环境变量或密钥存储。
- 高风险工具已配置权限、确认、幂等和审计。
- 沙箱类 Agent 已启用 sandbox 和 snapshot store。
- OpenTelemetry 或等价遥测出口已配置。
- 限流预算符合租户策略。
- 回滚开关已准备：禁用 Agent、工具、RAG 回答、模型提供方和 Skill 版本。

Kubernetes 部署建议：

- API 服务无状态部署，多副本。
- Redis 或 MySQL 承载共享状态和预算计数。
- 对象存储承载工作区和沙箱快照。
- 使用 Secret 管理模型 key 和企业系统凭证。
- 使用 readiness/liveness probe。
- 将日志、trace、metrics 接入统一观测平台。

## 12. 团队学习路径

推荐按章节分工学习：

| 学习对象 | 推荐章节 | 目标 |
|---|---|---|
| 新后端同学 | 1、2、3、4 | 能启动项目并理解聊天主链路 |
| RAG 负责人 | 5、8、9 | 能接入知识源并保证权限过滤 |
| 工具集成负责人 | 6、8 | 能注册企业工具并控制高风险操作 |
| 平台工程师 | 7、11 | 能部署多副本并处理状态、工作区、限流和 fallback |
| 安全合规同学 | 6、8、11 | 能审查权限、脱敏、审计和回滚 |
| 前端同学 | 4、5、6、9 | 能实现聊天、引用来源、工具确认和运营视图 |
| 多 Agent 负责人 | 10 | 能设计 Supervisor、专家 Agent 和 handoff trace |

每个章节学习后建议输出一份小结：

- 本章解决什么问题。
- 依赖哪些配置。
- 涉及哪些 API。
- 有哪些测试或验收点。
- 生产环境最容易出错的地方。

## 13. 验收和回滚

详细发布手册见 `docs/release-readiness.md`。

MVP 验收：

- 非流式和流式聊天可用。
- 会话按 tenant/user/agent/session 隔离。
- 模型 provider 可以替换。

RAG 验收：

- 可注册知识源。
- 可上传文档并检索。
- 返回引用来源。
- 无权限知识不会进入回答。
- 证据不足时返回无答案。

工具验收：

- 只读工具可执行。
- 高风险工具需要确认或审批。
- 未授权用户被拒绝。
- 非白名单参数被拒绝。
- 变更类工具支持幂等。
- 审计记录可查询且已脱敏。

生产验收：

- 多副本不能使用本地状态。
- 状态 key 包含租户、用户、Agent、会话和隔离范围。
- 生产代码类 Agent 必须使用沙箱。
- 沙箱配置快照存储。
- 限流、fallback、遥测和超时生效。

回滚方式：

- 禁用新 Agent。
- 禁用指定工具。
- 关闭 RAG-backed answer。
- 切回旧模型 provider。
- 回滚 Skill 版本。
- 将流量切回上一版本服务。

回滚不能删除审计记录。高风险操作的审计和确认记录必须保留到租户合规策略要求的期限。
