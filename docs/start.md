# HarnessAgent 个人版 Agent 学习、部署与使用指南

本文面向三类读者：

- 个人版产品和使用者：理解个人 Agent、工作区、记忆、工具和技能的能力边界。
- 后端和平台工程师：学习 Spring Boot、AgentScope Java v2、HarnessAgent、RAG、工具治理和生产运行时的实现路径。
- 维护者：掌握部署配置、状态存储、个人授权、最小审计、限流、回滚和故障排查方式。

建议按章节学习。新同学先读第 1 到第 4 章即可跑通本地 MVP；负责生产化的人继续读第 7 到第 11 章；涉及历史兼容迁移时同时阅读迁移盘点文档。

## 1. 平台目标和边界

HarnessAgent 当前目标是个人版完整 Agent 应用。个人版以单个 owner 的 Agent、会话、工作区、记忆、知识、工具、技能和多 Agent 协作为核心，不把组织治理、集中报表或阶段准入作为主验收目标。

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

历史字段仍可能出现在迁移脚本、旧数据说明或隐藏诊断入口中。默认个人路径只使用 owner、Agent、session 和 workspace；新写入的状态 key 使用 owner 形态，旧 key 只在迁移或恢复验证中读取一次。

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
- `src/main/java/com/harnessagent/tooling`：工具注册、执行、权限、参数、确认、幂等和最小记录，按 `domain`、`application`、`execution`、`persistence` 分层。
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
curl -i 'http://localhost:8080/api/console/user?ownerId=owner-a&agentId=personal-assistant'
```

默认端口是 `8080`。基础配置在 `src/main/resources/application.yml`。

## 4. MVP 聊天能力

聊天请求默认只要求 owner、Agent、session 和 message。非流式聊天示例：

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "ownerId": "owner-a",
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
    "ownerId": "owner-a",
    "agentId": "personal-assistant",
    "sessionId": "session-a",
    "message": "请用三句话介绍个人 Agent 能力"
  }'
```

会话相关 API：

- `GET /api/sessions?ownerId=owner-a&agentId=personal-assistant`
- `GET /api/messages?ownerId=owner-a&agentId=personal-assistant&sessionId=session-a`
- `DELETE /api/sessions/{sessionId}?ownerId=owner-a&agentId=personal-assistant`

运行时隔离规则：

```text
ownerId        = <owner-id>
agentId        = <personal-agent-id>
sessionId      = <personal-session-id>
ownerStateKey  = owner:<owner-id>:agent:<agent-id>:session:<session-id>:scope:<scope>
```

个人 Agent 工作区按 owner 和 agent 隔离。默认根目录来自 Agent 配置的 `workspace`，并在其下按 owner 创建独立目录；未配置时使用 `.harness-agent/personal/workspaces/<agentId>/<ownerId>`。初始化后目录包含 `persona/`、`memory/`、`skills/`、`subagents/`、`plans/`、`sessions/`、`artifacts/` 和 `workspace.json` 元数据。工作区路径解析只接受相对路径或 `workspace://` URI，并拒绝绝对路径与 `..` 穿越。服务层已经支持上传内容、保存生成文件、定位引用 metadata、下载和删除；前端文件浏览视图由后续工作台任务接管。

长任务恢复会在 `sessions/session-<hash>/runtime.json` 中记录工作区和沙箱快照引用、任务 ID 与计划路径。应用重启或后台任务恢复时，`PersonalWorkspaceRuntimeStateService` 可按引用恢复 workspace root 或 session 级 sandbox 目录。

上下文接近配置阈值时，`ContextCompactionService` 会生成结构化摘要并写入 `workspace://sessions/.../compactions/context-*.json`，发送给模型的历史会被折叠为系统摘要消息和当前轮次消息，原始 session 消息仍保留。摘要字段覆盖目标、当前状态、关键发现、决策、文件引用、下一步以及源消息 ID。

Plan Mode 通过 `PlanModeService` 只读生成计划文件，落盘到 `workspace://plans/plan-*.md`。计划模式下工具执行会剥离内部 `__planMode` 标记；只读工具可执行，mutating、高风险或沙箱类工具会在 preflight 阶段拒绝，避免计划阶段产生副作用。

流式事件包含展示 channel：`USER_VISIBLE`、`TOOL_EVENT`、`PLAN_UPDATE`、`SYSTEM_NOTICE` 和 `DIAGNOSTIC`。SSE 的 `type` 和 `kind` 兼容旧客户端，新增 `channel` 用于前端默认只展示用户可见输出，并在 trace/诊断视图中展示工具、计划和系统事件。

## 5. 知识库 RAG

RAG 目标不是简单向量检索，而是可追溯的个人知识和记忆访问。个人版默认按 owner、Agent、工作区和知识源可见范围过滤。

当前 MVP 的检索实现是轻量词法检索：`vectorScore` 表示 token overlap/Jaccard 风格评分，不是 embedding-backed vector database 检索。真实 embedding、向量索引、向量数据库 adapter 和 reranking 需要通过后续 OpenSpec 变更单独接入。

知识源注册示例：

```bash
curl -X POST http://localhost:8080/api/knowledge/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "ownerId": "owner-a",
    "agentId": "personal-assistant",
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
    "ownerId": "owner-a",
    "agentId": "personal-assistant",
    "query": "发票多久提交",
    "limit": 3
  }'
```

聊天时启用 RAG：

```json
{
  "ownerId": "owner-a",
  "agentId": "personal-assistant",
  "sessionId": "session-rag",
  "message": "发票多久提交？",
  "knowledgeEnabled": true,
  "knowledgeLimit": 3
}
```

RAG 行为要求：

- 检索前按 owner 和 Agent 过滤。
- 检索后按 source visibility、allowed owners 和个人授权过滤。
- 只有可访问证据会进入 Agent prompt 和引用来源。
- 证据不足时返回无答案，不让模型编造带来源回答。
- 知识源删除、撤销或版本失效后，旧切片不能继续被引用。

## 6. 受治理的工具调用

工具治理覆盖以下环节：

- 工具注册表：schema、归属系统、归属人、风险等级、权限策略和记录策略。
- 风险分级：只读工具和高风险工具。
- 权限检查：owner、Agent、工具策略、确认策略和禁用策略。
- 参数校验：必填字段、白名单字段、枚举值约束。
- 高风险确认：owner 确认或沙箱策略。
- 沙箱：Shell、SQL、代码或不可信工具必须先确认，再通过沙箱策略选择本地子进程、Docker 或远端沙箱适配点。
- 幂等：会修改外部系统的工具必须提供 idempotency key。
- 最小记录：保存脱敏输入、脱敏输出、耗时、状态、确认上下文和失败原因。
- MCP：MCP-backed 工具也必须走同一治理链路；当前 MVP 验收的是 MCP 作为受治理工具来源类型，不代表已经接入真实外部 MCP client/server 执行。

注册只读工具示例：

```bash
curl -X POST http://localhost:8080/api/tools \
  -H 'Content-Type: application/json' \
  -d '{
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
    "allowedOwnerIds": ["owner-a"],
    "allowedAgents": ["personal-assistant"]
  }'
```

执行工具示例：

```bash
curl -X POST http://localhost:8080/api/tools/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "ownerId": "owner-a",
    "agentId": "personal-assistant",
    "sessionId": "session-a",
    "toolId": "<tool-id>",
    "parameters": {
      "customerId": "C-1001",
      "token": "secret"
    }
  }'
```

高风险工具必须先返回 `PENDING_CONFIRMATION`，owner 确认后才能执行。Shell、SQL、代码和不可信工具即使声明为只读，也会先进入确认态；确认后由 `SandboxExecutionPolicyService` 选择沙箱策略，并通过本地子进程、Docker 或远端沙箱 executor 适配点执行。当前真实 runner 尚未接线时，适配器会返回未支持结果，不会绕过普通应用进程直接运行命令。变更类工具还必须带 `idempotencyKey`，同一个 key 和同一组参数会复用已有结果，同一个 key 但参数不同会返回幂等冲突。

工具记录可以在工作台 trace/diagnostics 视图中按 owner 查看；默认使用脱敏后的输入、输出摘要、状态和失败原因。

## 7. 生产运行时

生产运行时要解决几个问题：

- 多副本状态共享。
- owner 感知状态 key。
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

个人版主路径使用 owner 身份、工作区边界、工具/Skill 个人授权、沙箱和最小记录。历史治理模型只在迁移盘点和旧数据恢复说明中保留，不作为默认产品路径。

个人版安全原则：

- owner 身份和个人工作区是默认隔离边界。
- 高风险工具、Skill、代码、Shell、SQL、外部写操作和网络副作用必须经过 owner 确认或沙箱策略。
- RAG、记忆、文件和工具结果不能越过 owner、Agent、session 和工作区边界。
- 防 Prompt Injection，不允许检索内容或用户输入扩大工具和数据权限。
- 模型 key、个人系统凭证和工具 token 存储在配置或密钥存储中，不能硬编码。
- 日志、trace、记录和诊断信息必须脱敏。
- 最小记录只保存状态、原因码、摘要和可恢复引用，不保存原始 prompt、文件内容、工具原始结果或密钥。

### 身份接入契约

本地开发请求使用 `ownerId` 和 `agentId`。生产环境应由可信本地宿主、网关或认证中间件注入权威 owner 头，服务端以可信头为准：

| Header | 含义 |
|---|---|
| `X-Owner-Id` | 权威 owner 标识 |
| `X-Identity-Provider` | `local`、`host`、`gateway` 或其他可信来源 |

如果可信 owner 头存在，请求体中的 `ownerId` 与可信头不一致，服务端会拒绝请求。

### Prompt Injection 与工具安全

检索内容和用户输入都只能作为数据处理，不能改变系统策略。遇到以下内容应拒绝、降级或进入人工复核：

- 要求忽略系统提示词或平台策略。
- 要求泄露 API key、token、凭证或系统提示词。
- 要求调用未授权工具。
- 要求扩大 owner、Agent、workspace 或资源权限。
- 在工具参数中注入白名单外字段。

工具调用仍必须经过 `ToolService` 的权限、参数、确认、幂等和最小记录链路。

### 密钥管理

模型 key、个人系统凭证和工具 token 必须通过批准的 secret 来源解析，例如：

- `env:DASHSCOPE_API_KEY`
- Kubernetes Secret 注入的环境变量。
- 本地或宿主提供的密钥服务。

生产环境禁止把真实 key 写入 `application.yml`、trace attributes、异常消息或诊断导出。

### 脱敏和诊断导出

默认脱敏字段包括：

- `token`
- `password`
- `secret`
- `apiKey`
- `api_key`
- `authorization`
- `credential`

文本中常见邮箱和手机号也会被脱敏。日志、trace、工具记录、安全记录和诊断导出都应复用同一套脱敏策略。

### 高风险记录

高风险记录应覆盖：

- 高风险工具确认。
- 模型响应导致的安全拦截。
- Prompt Injection 拒绝。
- 人工干预。
- Skill 启用、禁用、升级、锁定和回滚。

这些记录不能被业务回滚删除。

### 个人 Skill 集成

`PersonalSkillService` 支持扫描本地 Skill 仓库，并在接口层预留 Git、MySQL 和 PostgreSQL 仓库类型；后续适配器可以复用相同的元数据、验证、权限和生命周期模型。

本地 Skill 目录以 `skill.json` 为入口，推荐结构如下：

```text
skills/
  file-analyzer/
    1.0.0/
      skill.json
      SKILL.md
      examples/basic.md
```

`skill.json` 记录名称、描述、版本、触发条件、权限、资源、样例和适用 Agent：

```json
{
  "name": "file-analyzer",
  "description": "Analyze workspace files",
  "version": "1.0.0",
  "triggers": ["analyze file"],
  "permissions": {
    "files": ["workspace://docs/**"],
    "tools": ["workspace.read"],
    "network": false,
    "sandbox": false,
    "memory": true
  },
  "resources": ["SKILL.md"],
  "examples": ["examples/basic.md"],
  "agentIds": ["personal-assistant"]
}
```

验证入口会检查必需元数据、引用资源、基本执行样例，并拒绝逃逸出 Skill 目录的资源路径。聊天执行时，如果用户意图和 Agent 匹配已启用或锁定的 Skill，系统会把 `SKILL.md` 等资源注入本轮 Agent 执行上下文；没有匹配 Skill 时保持普通聊天行为。Skill 请求的文件、工具、网络、沙箱和记忆权限会在执行前进入个人授权和权限检查。

当前个人 Skill API：

| API | 用途 |
|---|---|
| `GET /api/skills` | 按 owner 查看个人 Skill 元数据和状态 |
| `POST /api/skills/refresh-local` | 刷新本地 Skill 仓库 |
| `POST /api/skills/validate-local` | 验证本地 Skill 目录 |
| `PATCH /api/skills/{skillName}/{version}/enable` | 启用指定版本 |
| `PATCH /api/skills/{skillName}/{version}/disable` | 禁用指定版本 |
| `PATCH /api/skills/{skillName}/{version}/upgrade` | 升级到指定版本 |
| `PATCH /api/skills/{skillName}/{version}/rollback` | 回滚到指定版本 |
| `PATCH /api/skills/{skillName}/{version}/lock` | 锁定指定版本，阻止后续升级 |

`refresh-local` 和 `validate-local` 请求体需要携带 `ownerId` 和 `agentId`。服务端会按该 owner 和 Agent 初始化个人工作区，并只允许扫描或验证 `workspace/skills/` 下的真实路径；空的 `repositoryRoot` 会默认扫描整个 `skills/` 目录。仓库扫描有深度、数量和文件大小上限，资源路径会通过 realpath 边界校验并拒绝符号链接逃逸。

启用、禁用、升级、回滚和版本锁定都会写入个人最小记录，details 只包含 Skill 名称、版本、状态和来源类型等 allowlist 字段。

安全治理学习建议：

1. 先阅读 owner 身份和工作区边界。
2. 再阅读数据权限：确认同一个 owner 通过 RAG、工具、记忆、文件和 trace 看到的数据一致。
3. 然后阅读 Prompt/工具安全：确认模型输出、检索内容和用户输入都不能扩大权限。
4. 最后阅读密钥、脱敏和 Skill 生命周期：确认生产环境不会把凭证和敏感数据写进日志或 trace。

安全验收清单：

- 未认证请求在生产网关层返回 401。
- 已认证但缺少资源权限的请求返回 403 或受控拒绝。
- 请求体伪造 `ownerId` 时不会覆盖可信 owner 头。
- RAG、工具、记忆、文件和 trace 视图不会暴露越权数据。
- Prompt Injection 会被拒绝或降级。
- 密钥只通过批准的 Secret 来源解析。
- 日志、trace、记录和诊断导出无明文 token、API key、邮箱或手机号。
- 高风险工具、人工干预和 Skill 生命周期有最小记录。

## 9. 个人工作台和诊断

个人版前端是个人 Agent 工作台，优先展示聊天、任务计划、工作区文件、知识/记忆、工具确认、技能、trace、模型配置和本地诊断。历史控制台入口只允许作为隐藏诊断或迁移工具存在，不属于默认产品路径。

个人工作台主视图：

- 聊天：流式输出、历史、引用来源、文件附件、工具确认和子 Agent 状态。
- 任务和计划：计划文件、当前步骤、阻塞点、完成状态和下一步。
- 工作区文件：浏览、预览、上传、下载、删除和消息引用跳转。
- 知识和记忆：查看、添加、删除、导出和重建。
- 工具和技能：启用、禁用、配置、授权、测试和版本锁定。
- Trace 和诊断：模型、工具、RAG、子 Agent、错误和可操作诊断。

控制台实现时不要把页面做成宣传页。第一屏应直接提供可用工作台：会话列表、聊天区、引用来源、工具确认和状态信息。

当前后端提供 `/api/console/**` 聚合接口，供个人工作台和开发诊断使用：

| API | 用途 |
|---|---|
| `GET /api/console/user` | 当前 owner 的聊天工作台：会话、消息、引用、工具状态、确认提示 |
| `GET /api/console/agents` | 当前 owner 的 Agent 列表和配置视图 |
| `PATCH /api/console/agents/{agentId}/prompt` | 更新当前 owner 的 Agent Prompt 并写入最小记录 |
| `PATCH /api/console/agents/{agentId}/config` | 更新当前 owner 的 Agent 模型、工作区和压缩配置 |
| `GET /api/console/tools` | 当前 owner 可见工具、分类、启用状态和权限视图 |
| `PATCH /api/console/tools/{toolId}/enabled` | 启用或禁用当前 owner 的已注册工具 |
| `GET /api/console/knowledge` | 当前 owner 的知识源版本、可见性、索引状态和撤销视图 |
| `PATCH /api/console/knowledge/{sourceId}/revoke` | 撤销当前 owner 的知识源 |
| `GET /api/console/skills` | 当前 owner 的 Skill 仓库、版本和状态视图 |
| `GET /api/console/metrics` | 当前 owner 的会话、模型、工具、RAG、失败、耗时和反馈指标 |
| `GET /api/console/cost` | 当前 owner 按 Agent 和模型提供方聚合的成本和用量 |

工作台 API 使用 owner 查询参数和可信 owner 头：

```bash
curl 'http://localhost:8080/api/console/user?ownerId=owner-a&agentId=personal-assistant' \
  -H 'X-Owner-Id: owner-a'
```

更新 Agent Prompt 示例：

```bash
curl -X PATCH 'http://localhost:8080/api/console/agents/personal-assistant/prompt?ownerId=owner-a' \
  -H 'Content-Type: application/json' \
  -H 'X-Owner-Id: owner-a' \
  -d '{"systemPrompt":"你是个人 Agent，回答必须遵守 owner 权限、引用和工具确认要求。"}'
```

工作台验收要点：

- 非当前 owner 不能访问 Agent、工具、知识库、Skill、指标、成本或 trace。
- Prompt 更新、Skill 生命周期、高风险工具确认和人工干预必须进入最小记录。
- 指标和成本报表不能包含原始 prompt、token、凭证或敏感业务字段。

## 10. 多 Agent 编排

多 Agent 编排已具备个人版后端基础能力，后续工作台任务会补齐可视化操作入口和更完整的后台任务 UX。

专家 Agent 示例：

- 知识库 Agent。
- 数据分析 Agent。
- 审批 Agent。
- 客服 Agent。
- 本地维护 Agent。
- 代码 Agent。

Supervisor 负责：

- 理解任务意图。
- 判断用户权限。
- 选择专家 Agent。
- 规划步骤。
- 记录 routing、handoff、工具调用和最终回答组装 trace。

跨 Agent 共享上下文时必须遵守权限边界，只传递子 Agent 契约和用户权限允许的数据。

子 Agent 规格可保存到个人工作区 `subagents/` 目录，规格包含名称、用途、输入/输出契约、允许工具、允许技能、知识源和上下文边界。上下文边界默认不共享长期记忆、文件和原始工具输出；只有显式允许的 key 和类别会进入 handoff。

当前后端提供 `/api/orchestration/**` 编排接口：

| API | 用途 |
|---|---|
| `POST /api/orchestration/agents` | 注册专家 Agent，保存用途、契约、权限、工具、知识访问和归属人 |
| `POST /api/orchestration/route` | Supervisor 根据任务意图、权限、Agent 能力和上下文边界路由，可选择同步或后台委派 |
| `GET /api/orchestration/agents/{agentId}/tool` | 将已批准子 Agent 暴露为 Agent-as-Tool，并复用工具权限、参数校验、最小记录和 trace |
| `GET /api/orchestration/traces` | 查询 Supervisor 决策、候选 Agent、子 Agent 调用、handoff、失败和结果组装 trace |

注册专家 Agent 示例：

```bash
curl -X POST http://localhost:8080/api/orchestration/agents \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "knowledge-agent",
    "purpose": "回答制度知识问题",
    "inputContract": "text question",
    "outputContract": "grounded answer with citations",
    "allowedTools": ["crm.customer.lookup"],
    "allowedSkills": ["policy-search"],
    "allowedKnowledgeSources": ["policy-handbook"],
    "contextBoundary": {
      "shareMemory": false,
      "shareFiles": false,
      "shareToolOutputs": false,
      "shareRetrievedKnowledge": true,
      "allowedKeys": ["question", "citations"]
    },
    "ownerId": "owner-a",
    "approved": true
  }'
```

Supervisor 路由示例：

```bash
curl -X POST http://localhost:8080/api/orchestration/route \
  -H 'Content-Type: application/json' \
  -H 'X-Owner-Id: owner-a' \
  -d '{
    "ownerId": "owner-a",
    "supervisorAgentId": "supervisor",
    "taskIntent": "制度知识",
    "task": "查询报销制度",
    "context": {
      "question": "发票多久提交？",
      "citations": ["policy-handbook:v1"],
      "secret": "不应共享"
    },
    "delegationMode": "SYNC",
    "failureStrategy": "FALLBACK_TO_SUPERVISOR"
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
- Agent-as-Tool trace 引用。
- 失败、升级、后台 `BACKGROUND_RUNNING` 接收态、后台完成 trace 或 fallback 原因。

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
- 高风险工具已配置权限、确认、幂等和最小记录。
- 沙箱类 Agent 已启用 sandbox 和当前已接线的 snapshot store。
- OpenTelemetry 或等价遥测出口已配置。
- 限流预算符合 owner 和 Agent 策略。
- 回滚开关已准备：禁用 Agent、工具、RAG 回答、模型提供方和 Skill 版本。

Kubernetes 部署建议：

- API 服务无状态部署，多副本。
- Redis 或 MySQL 承载共享状态和预算计数。
- 远程文件系统或对象存储承载工作区；当前沙箱快照生产验收路径使用 JDBC snapshot，OSS/S3/MinIO snapshot 需要后续接入。
- 使用 Secret 管理模型 key、个人系统凭证和工具 token。
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

核心表覆盖会话消息、知识源和切片、工具定义、工具执行记录、幂等、待确认操作、安全记录、预算计数、Agent state、telemetry、snapshot metadata 和 snapshot content。完整表名、字段用途、索引维度和 MySQL metadata 验收 SQL 以 [durable-persistence-schema.md](durable-persistence-schema.md) 为准。

所有生产查询路径都包含 owner、agent、session、time 或 resource 维度索引。JSON 文本字段包括权限策略、工具参数 schema、工具输出 schema、HITL pending 参数、记录策略、遥测 attributes、RAG 访问列表和工具输入输出摘要；这些字段保存运行治理状态，不应写入 schema comment。备份恢复时至少同时恢复 session、agent state、tool idempotency、tool pending confirmation、安全记录、telemetry 和 snapshot 表；只恢复业务消息而不恢复 Agent state 会导致 AgentScope memory 和 compaction 状态丢失。

聊天恢复策略以 `SessionStore` 作为消息历史来源、以 `AgentStateStore` 作为 AgentScope 上下文来源。若同一 session 已存在 AgentScope state，后续调用只把当前轮消息交给 runtime，避免把已经由 AgentScope 管理的历史再次回放；若 AgentScope state 不存在，则使用持久化消息历史初始化上下文。

### 11.3 发布前双实例验证

发布前至少执行一次双实例恢复验证：

1. 用同一 MySQL schema 启动两个服务实例。
2. 在实例 A 写入会话消息、知识源、工具幂等结果、AgentScope state 和 workspace snapshot。
3. 在实例 B 读取同一 owner/agent/session 的数据。
4. 确认 owner-b 不能读取 owner-a 的会话、知识、记录或 snapshot。
5. 删除 session 或 snapshot 后，确认另一个实例立即看不到对应数据。
6. 调用个人工作台、聊天、RAG、工具确认和 trace smoke，确认生产运行时检查全部通过；如返回阻塞原因，先处理再发布。

回滚时优先切回上一版配置或上一版服务实例，不要删除 durable store 中的最小记录、幂等、telemetry 和 snapshot 记录。确需回退 schema 时，先完成数据库备份并确认没有新版本服务继续写入。

## 12. 团队学习路径

推荐按章节分工学习：

| 学习对象 | 推荐章节 | 目标 |
|---|---|---|
| 新后端同学 | 1、2、3、4 | 能启动项目并理解聊天主链路 |
| RAG 负责人 | 5、8、9 | 能接入知识源并保证权限过滤 |
| 工具集成负责人 | 6、8 | 能注册个人工具并控制高风险操作 |
| 平台工程师 | 7、11 | 能部署多副本并处理状态、工作区、限流和 fallback |
| 安全合规同学 | 6、8、11 | 能审查权限、脱敏、审计和回滚 |
| 前端同学 | 4、5、6、9 | 能实现聊天、引用来源、工具确认和个人工作台视图 |
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
- 会话按 owner/agent/session 隔离。
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
- 最小记录可查询且已脱敏。

生产验收：

- 多副本不能使用本地状态。
- 状态 key 包含 owner、Agent、会话和隔离范围。
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

回滚不能删除最小记录。高风险操作的记录和确认记录必须保留到个人数据保留策略要求的期限。
