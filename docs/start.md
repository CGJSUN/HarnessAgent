# HarnessAgent 个人版 Agent 学习、部署与使用指南

本文面向三类读者：

- 个人版产品和使用者：理解个人 Agent、工作区、记忆、工具和技能的能力边界。
- 后端和平台工程师：学习 Spring Boot、AgentScope Java v2、HarnessAgent、RAG、工具治理和生产运行时的实现路径。
- 维护者：掌握部署配置、状态存储、个人授权、最小审计、限流、回滚和故障排查方式。

建议按章节学习。新同学先读第 1 到第 4 章即可跑通本地 MVP；负责生产化的人继续读第 7 到第 11 章；涉及企业兼容迁移时同时阅读 [enterprise-to-personal-migration-inventory.md](enterprise-to-personal-migration-inventory.md)。

## 1. 平台目标和边界

HarnessAgent 当前目标已经从企业级受治理 Agent 平台调整为个人版完整 Agent 应用。个人版以单个 owner 的 Agent、会话、工作区、记忆、知识、工具、技能和多 Agent 协作为核心，不再把企业多租户治理、企业 RBAC、审计报表、运营后台和发布门禁作为主验收目标。

第一阶段先交付一个可靠的个人 Agent 闭环：

- Spring Boot + Maven + JDK 17 项目。
- `POST /api/chat` 非流式聊天。
- `POST /api/chat/stream` 流式聊天。
- owner、Agent、会话和工作区隔离。
- 模型提供方抽象。
- 会话和消息管理。

随后逐步扩展：

- 个人知识库、记忆和 RAG。
- 个人工具调用、Human-in-the-loop、沙箱和最小审计。
- 工作区文件、计划模式、channel、快照和上下文压缩。
- 个人技能仓库和多 Agent 编排。
- 个人 Agent Web 工作台。

早期企业字段仍可能出现在 API、持久化表和控制台中。个人版将 `tenantId` 视为内部兼容隔离 key，默认映射为字面量 `personal`，将 `userId` 映射为 owner，并派生 `runtimeUserId=personal:<ownerId>`；`roles`、`departments`、admin/ops/audit 视图和 release gate API 只作为遗留兼容或诊断能力，不再是个人版主流程。

个人版仍不允许 Agent 绕过安全边界直接读取工作区外文件、调用未授权工具、发送外部副作用请求、改写本地/外部系统或执行不可信代码。所有高风险能力必须经过参数校验、个人确认、沙箱、幂等和最小审计控制。

## 2. 总体架构

当前平台按以下层次组织：

```text
用户入口
  Web 工作台 / 本地 API / 可选协议入口
        |
        v
Spring Boot API 层
  chat / stream / sessions / workspace / knowledge / tools / skills / traces
        |
        v
个人 Agent 服务层
  Agent Core / Workspace / Memory RAG / Tool HITL / Skill / Multi-Agent
        |
        v
HarnessAgent + AgentScope Java v2
  Agent / Message Event / Model / Permission / Tool / Context State / Harness
        |
        v
基础设施
  H2 或 MySQL / local-json 或 Redis state / Workspace / Sandbox / Snapshot / Telemetry
```

主要代码目录：

- `src/main/java/com/harnessagent/api`：HTTP API 层，按 `controller`、`request`、`response` 拆分控制器和契约模型。
- `src/main/java/com/harnessagent/chat`：聊天主链路，`application` 放编排服务，`domain` 放命令和结果模型。
- `src/main/java/com/harnessagent/rag`：知识源、切片、检索、引用和反馈，按 `domain`、`application`、`retrieval`、`persistence` 分层。
- `src/main/java/com/harnessagent/tooling`：工具注册、执行、权限、参数、确认、幂等和审计，按 `domain`、`application`、`execution`、`audit`、`persistence` 分层。
- `src/main/java/com/harnessagent/security`：身份、授权、脱敏、Prompt 安全、Skill 治理和审计，按 `domain`、`application`、`persistence` 分层。
- `src/main/java/com/harnessagent/production`：生产运行时，按 `config`、`health`、`state`、`budget`、`telemetry`、`snapshot`、`workspace`、`infrastructure` 分层。
- `src/main/java/com/harnessagent/console`：控制台聚合服务和只读视图，按 `application`、`view` 分层。
- `src/main/java/com/harnessagent/orchestration`：多 Agent 路由、handoff、trace 和 Agent-as-Tool，按 `domain`、`application` 分层。
- `src/main/java/com/harnessagent/agent`：AgentScope runtime 适配，按 `runtime`、`application` 分层。
- `src/main/java/com/harnessagent/session`：会话消息模型和存储，按 `domain`、`persistence` 分层。

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

当前已实现聊天请求仍兼容企业 MVP 契约，可能携带：

- `tenantId`
- `userId`
- `agentId`
- `sessionId`
- `message`

个人版目标是默认只要求 owner、agent、session、message；上述企业字段由 `personal` profile 自动映射或作为 legacy 字段读取。当前兼容 API 的非流式聊天示例：

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "userId": "user-a",
    "agentId": "personal-assistant",
    "sessionId": "session-a",
    "message": "帮我总结一下个人 Agent 能做什么"
  }'
```

流式聊天：

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "userId": "user-a",
    "agentId": "personal-assistant",
    "sessionId": "session-a",
    "message": "请用三句话介绍个人 Agent 能力"
  }'
```

会话相关 API：

- `GET /api/sessions?tenantId=tenant-a&userId=user-a&agentId=personal-assistant`
- `GET /api/sessions/{sessionId}/messages?tenantId=tenant-a&userId=user-a&agentId=personal-assistant`
- `DELETE /api/sessions/{sessionId}?tenantId=tenant-a&userId=user-a&agentId=personal-assistant`

运行时隔离规则：

```text
RuntimeContext.userId    = tenantId + ":" + userId
RuntimeContext.sessionId = agentId + ":" + sessionId
生产状态 key = tenant + user + agent + session + isolationScope
```

个人版默认映射：

```text
tenantId       = personal
userId         = <ownerId>
runtimeUserId  = personal:<ownerId>
agentId        = <personal-agent-id>
sessionId      = <personal-session-id>
```

个人 Agent 工作区按 owner 和 agent 隔离。默认根目录来自 Agent 配置的 `workspace`，并在其下按 owner 创建独立目录；未配置时使用 `.harness-agent/personal/workspaces/<agentId>/<ownerId>`。初始化后目录包含 `persona/`、`memory/`、`skills/`、`subagents/`、`plans/`、`sessions/`、`artifacts/` 和 `workspace.json` 元数据。工作区路径解析只接受相对路径或 `workspace://` URI，并拒绝绝对路径与 `..` 穿越。服务层已经支持上传内容、保存生成文件、定位引用 metadata、下载和删除；前端文件浏览视图由后续工作台任务接管。

## 5. 知识库 RAG

RAG 目标不是简单向量检索，而是可追溯的个人知识和记忆访问。企业版中的租户、部门和角色 ACL 在个人版中降级为遗留兼容；个人版默认按 owner、Agent、工作区和知识源可见范围过滤。

当前 MVP 的检索实现是轻量词法检索：`vectorScore` 表示 token overlap/Jaccard 风格评分，不是 embedding-backed vector database 检索。真实 embedding、向量索引、向量数据库 adapter 和 reranking 需要通过后续 OpenSpec 变更单独接入。

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
- 沙箱：Shell、SQL、代码或不可信工具必须先确认，再通过沙箱策略选择本地子进程、Docker 或远端沙箱适配点。
- 幂等：会修改外部系统的工具必须提供 idempotency key。
- 审计：记录脱敏输入、脱敏输出、耗时、状态、审批人和失败原因。
- MCP：MCP-backed 工具也必须走同一治理链路；当前 MVP 验收的是 MCP 作为受治理工具来源类型，不代表已经接入真实外部 MCP client/server 执行。

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

高风险工具必须先返回 `PENDING_CONFIRMATION`，用户确认或审核通过后才能执行。Shell、SQL、代码和不可信工具即使声明为只读，也会先进入确认态；确认后由 `SandboxExecutionPolicyService` 选择沙箱策略，并通过本地子进程、Docker 或远端沙箱 executor 适配点执行。当前真实 runner 尚未接线时，适配器会返回未支持结果，不会绕过普通应用进程直接运行命令。变更类工具还必须带 `idempotencyKey`，同一个 key 和同一组参数会复用已有结果，同一个 key 但参数不同会返回幂等冲突。

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
- 按 owner、Agent、模型提供方限流和预算，并支持个人 Agent 级预算覆盖。
- 模型 provider fallback。
- 流式响应、工具和沙箱超时。

关键配置：

```yaml
harness-agent:
  agents:
    personal-assistant:
      model-provider: dashscope
      model-name: qwen-plus
      # 可选：覆盖 provider 默认密钥引用；值通过 SecretStore 解析，不写入日志。
      model-api-key-ref: env:PERSONAL_DASHSCOPE_KEY
      fallback-providers: [echo]
      budget:
        request-limit: 1000
        token-limit: 1000000
  model-providers:
    dashscope:
      type: dashscope
      model-name: qwen-plus
      api-key-ref: env:DASHSCOPE_API_KEY
    personal-openai:
      type: openai-compatible
      model-name: gpt-4o-mini
      api-key-ref: env:OPENAI_API_KEY
      base-url: https://api.openai.com/v1
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
      mode: docker # 可选：local_process、docker、remote；生产高风险任务不允许 local_process
      image: harness-agent-sandbox:latest
      workspace-root: /workspace
      remote-endpoint: "" # mode=remote 时填写远端沙箱服务地址
    snapshot-store:
      type: jdbc
      uri: jdbc://snapshot
      implementation-wired: true
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
- code、shell、SQL、不可信执行类 Agent 必须使用沙箱；开发环境可选择本地子进程适配点，生产环境默认 Docker，也可配置远端沙箱适配点。
- 沙箱在生产环境必须配置已接线的快照存储；当前可验收的生产实现是 JDBC，OSS、S3、MinIO 仍是后续对象存储扩展入口。
- 超预算请求会在进入模型前被拒绝。
- 只有可重试模型失败才触发 fallback；权限、预算、参数错误不会 fallback。

## 8. 安全治理

本章保留了企业版治理实现的说明，用于理解遗留兼容层。个人版默认不要求企业 IdP、企业 RBAC、部门/角色数据权限或审批人流程；个人版主路径使用 owner 身份、工作区边界、工具/技能个人授权、沙箱和最小审计。

个人版安全原则：

- owner 身份和个人工作区是默认隔离边界。
- 高风险工具、技能、代码、Shell、SQL、外部写操作和网络副作用必须经过用户确认或沙箱策略。
- RAG、记忆、文件和工具结果不能越过 owner、Agent、session 和工作区边界。
- 防 Prompt Injection，不允许检索内容或用户输入扩大工具和数据权限。
- 模型 key、个人系统凭证和工具 token 存储在配置或密钥存储中，不能硬编码。
- 日志、trace、审计和诊断信息必须脱敏。
- 最小审计只记录状态、原因码、摘要和可恢复引用，不记录原始 prompt、文件内容、工具原始结果或密钥。

企业兼容治理原则：

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

### RBAC 权限矩阵（遗留兼容）

下面矩阵只描述早期企业控制台兼容行为，不作为个人版默认权限模型。

| 资源 | 普通员工 | 业务专家 | 审核人 | 管理员 | 审计员 |
|---|---|---|---|---|---|
| Agent 聊天 | 读/执行已授权 Agent | 读/执行已授权 Agent | 读/执行已授权 Agent | 管理 Agent | 按权限查看记录 |
| 知识源 | 读取可见知识 | 注册和维护授权知识 | 复核敏感知识 | 管理全部知识配置 | 查看知识访问审计 |
| 工具 | 执行已授权只读工具 | 维护业务工具元数据 | 审批高风险工具 | 注册、禁用、授权工具 | 搜索工具审计 |
| 管理操作 | 无 | 有限维护 | 审批 | 管理 | 只读审计 |
| 运营数据 | 自己范围 | 业务范围 | 审批范围 | 全局运营 | 审计范围 |
| Skill | 使用已发布版本 | 提议版本 | 审核版本 | 发布/禁用/回滚 | 查看生命周期审计 |

### 统一数据权限模型（遗留兼容）

RAG、工具输出、会话记忆、文件和审计搜索都应使用同一组权限维度：

- `tenantId`
- `userId`
- `roles`
- `departments`
- `resourceType`
- `permission`

跨租户数据永远不可见。个人版中该规则等价于不同 owner、Agent、session 和工作区之间默认不共享状态、文件、记忆、工具授权或 trace。

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

Agent 只能使用已批准且已发布的 Skill 版本。禁用后立即不可用，回滚时恢复目标已批准版本并保留审计。当前控制台 REST/UI 暂未开放可执行 Skill rollback，回滚仅作为发布预案或底层服务能力描述。

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

## 9. 个人工作台和遗留控制台

个人版目标是把前端重构为个人 Agent 工作台，优先展示聊天、任务计划、工作区文件、知识/记忆、工具确认、技能、trace、模型配置和本地诊断。早期企业控制台的管理端、运营端、审计搜索和发布门禁视图只保留为迁移期兼容或开发诊断。

个人工作台主视图：

- 聊天：流式输出、历史、引用来源、文件附件、工具确认和子 Agent 状态。
- 任务和计划：计划文件、当前步骤、阻塞点、完成状态和下一步。
- 工作区文件：浏览、预览、上传、下载、删除和消息引用跳转。
- 知识和记忆：查看、添加、删除、导出和重建。
- 工具和技能：启用、禁用、配置、授权、测试和版本锁定。
- Trace 和诊断：模型、工具、RAG、子 Agent、错误和可操作诊断。

遗留控制台分三类视图：

- 用户端：聊天、流式输出、历史、引用来源、文件上传、工具状态、确认提示。
- 管理端：Agent、Prompt、模型、工作区 profile、工具、知识库、Skill、权限。
- 运营端：会话、模型调用、工具调用、RAG 命中、失败、耗时、Token 成本、反馈和审计搜索。

控制台实现时不要把页面做成宣传页。第一屏应直接提供可用工作台：会话列表、聊天区、引用来源、工具确认和状态信息。

当前后端提供 `/api/console/**` 聚合接口，供遗留 Web 控制台、开发诊断或企业 IM 工作台使用：

| API | 用途 | 角色 |
|---|---|---|
| `GET /api/console/user` | 用户聊天控制台：会话、消息、引用、工具状态、确认提示 | 已授权用户 |
| `GET /api/console/agents` | Agent 列表和配置视图 | admin |
| `PATCH /api/console/agents/{agentId}/prompt` | 更新 Agent Prompt 并记录审计 | admin |
| `GET /api/console/tools` | 已注册工具、分类、启用状态和权限视图 | admin |
| `PATCH /api/console/tools/{toolId}/enabled` | 启用或禁用已注册工具 | admin |
| `GET /api/console/knowledge` | 知识源版本、权限、索引状态和撤销视图 | admin |
| `PATCH /api/console/knowledge/{sourceId}/revoke` | 撤销知识源；console 专用删除入口当前未开放 | admin |
| `GET /api/console/skills` | Skill 仓库、版本、审批状态和禁用视图 | admin |
| `PATCH /api/console/skills/{versionId}/approve` | 审批 Skill 版本 | admin |
| `PATCH /api/console/skills/{versionId}/publish` | 发布 Skill 版本 | admin |
| `PATCH /api/console/skills/{versionId}/disable` | 禁用 Skill 版本；rollback 业务方法存在但当前未开放 REST/UI | admin |
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
- 沙箱类 Agent 已启用 sandbox 和当前已接线的 snapshot store。
- OpenTelemetry 或等价遥测出口已配置。
- 限流预算符合租户策略。
- 回滚开关已准备：禁用 Agent、工具、RAG 回答、模型提供方和 Skill 版本。

Kubernetes 部署建议：

- API 服务无状态部署，多副本。
- Redis 或 MySQL 承载共享状态和预算计数。
- 远程文件系统或对象存储承载工作区；当前沙箱快照生产验收路径使用 JDBC snapshot，OSS/S3/MinIO snapshot 需要后续接入。
- 使用 Secret 管理模型 key 和企业系统凭证。
- 使用 readiness/liveness probe。
- 将日志、trace、metrics 接入统一观测平台。

### 11.1 Durable persistence 配置

`development` 和 `test` profile 默认使用内存实现或本地 JSON 状态，适合本地调试和单进程测试。个人版本地运行会把 AgentScope state 写入 `production.state-store.local-directory` 指向的 JSON 目录；应用重启后，同一 owner/agent/session 会恢复会话消息、AgentScope state 和待处理执行状态。`production` profile 禁止 AgentScope 状态回落到本地 JSON，需要显式配置 durable store。

默认生产配置以 MySQL/JDBC 为主：

```yaml
harness-agent:
  production:
    profile: production
    state-store:
      type: mysql
      mysql-dsn: ${HARNESS_AGENT_MYSQL_DSN}
      durable-implementation-wired: true
    durable-stores:
      session: mysql
      knowledge: mysql
      tool: mysql
      audit: mysql
      telemetry: mysql
      budget-counter: mysql
    telemetry:
      durable-store-enabled: true
    snapshot-store:
      type: jdbc
      uri: jdbc://snapshot
      implementation-wired: true
```

如果只把 AgentScope state 或预算计数切到 Redis：

```yaml
harness-agent:
  production:
    state-store:
      type: redis
      redis-uri: ${HARNESS_AGENT_REDIS_URI}
      durable-implementation-wired: true
    durable-stores:
      budget-counter: redis
```

Redis 当前用于 AgentScope state 和预算计数；会话、知识、工具、审计、telemetry 和 JDBC snapshot 仍使用 MySQL/JDBC。

### 11.2 Schema 初始化和备份恢复

Flyway common DDL 位于 `classpath:db/migration`，数据库专属注释 migration 位于 `classpath:db/vendor-migration/{vendor}`。首个版本 `V1__durable_persistence.sql` 创建核心表，H2/MySQL 的 V2 migration 为表和字段补充 metadata comments。完整表用途、字段用途、索引维度、JSON 文本字段和 MySQL metadata 验收 SQL 见 [durable-persistence-schema.md](durable-persistence-schema.md)。

核心表：

- `ha_session_messages`
- `ha_knowledge_sources`、`ha_knowledge_chunks`
- `ha_tool_definitions`、`ha_tool_audit_records`、`ha_tool_idempotency_records`
- `ha_security_audit`
- `ha_budget_counters`
- `ha_agent_state`
- `ha_telemetry_events`
- `ha_snapshot_metadata`、`ha_snapshot_content`

所有生产查询路径都包含 tenant、user、agent、session、time 或 resource 维度索引。JSON 文本字段包括权限策略、工具参数 schema、审计策略、遥测 attributes、RAG 访问列表和工具审计输入输出；这些字段保存业务治理状态，不应写入 schema comment。备份恢复时至少同时恢复 session、agent state、tool idempotency、audit、telemetry 和 snapshot 表；只恢复业务消息而不恢复 `ha_agent_state` 会导致 AgentScope memory 和 compaction 状态丢失。

聊天恢复策略以 `SessionStore` 作为消息历史来源、以 `AgentStateStore` 作为 AgentScope 上下文来源。若同一 session 已存在 AgentScope state，后续调用只把当前轮消息交给 runtime，避免把已经由 AgentScope 管理的历史再次回放；若 AgentScope state 不存在，则使用持久化消息历史初始化上下文。

### 11.3 发布前双实例验证

发布前至少执行一次双实例恢复验证：

1. 用同一 MySQL schema 启动两个服务实例。
2. 在实例 A 写入会话消息、知识源、工具幂等结果、AgentScope state 和 workspace snapshot。
3. 在实例 B 读取同一 tenant/user/agent/session 的数据。
4. 确认 tenant-b 不能读取 tenant-a 的会话、知识、审计或 snapshot。
5. 删除 session 或 snapshot 后，确认另一个实例立即看不到对应数据。
6. 调用 `/api/release/phase-gates`，确认 `Production Runtime` gate 为 `PASSED`；如果为 `BLOCKED`，先处理返回的 `failureReasons`。

回滚时优先切回上一版配置或上一版服务实例，不要删除 durable store 中的审计、幂等、telemetry 和 snapshot 记录。确需回退 schema 时，先完成数据库备份并确认没有新版本服务继续写入。

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
- 按发布预案回滚 Skill 版本；当前控制台 REST/UI 暂未开放可执行 rollback。
- 将流量切回上一版本服务。

回滚不能删除审计记录。高风险操作的审计和确认记录必须保留到租户合规策略要求的期限。
