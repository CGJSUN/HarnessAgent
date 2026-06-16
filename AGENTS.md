# AGENTS.md

## 项目概览

HarnessAgent 是一个企业级 Agent 平台 MVP，后端基于 Spring Boot 和 AgentScope Java，前端提供 React/Vite 控制台。这个系统面向受治理的企业助手场景，不是临时拼接聊天 SDK。

核心目标：

- 提供企业助手的非流式和流式聊天 API。
- 保持租户、用户、Agent、会话之间的状态隔离。
- 支持带权限过滤、引用来源和无答案策略的 RAG。
- 对工具调用进行权限校验、参数校验、确认/审批、幂等和审计治理。
- 覆盖生产运行时能力：持久化状态、工作区、快照、遥测、预算限制、fallback 和超时。
- 提供 Web 控制台，支持聊天、管理、运维、审计、发布验收和多 Agent 编排视图。

主要技术栈：

- Java 17
- Spring Boot 3.3.x
- AgentScope Java / HarnessAgent runtime
- Maven
- JUnit 5、AssertJ、Mockito、Reactor Test
- React 19、TypeScript、Vite、Vitest、Playwright
- 本地和测试默认使用 H2，生产可接入 MySQL、Redis、S3 类存储

## 仓库结构

```text
/
+-- pom.xml                         # Spring Boot 后端构建文件
+-- src/main/java/com/harnessagent
|   +-- agent/                      # AgentScope/HarnessAgent runtime 适配，按 runtime/application 分层
|   +-- api/                        # REST 控制器、请求和响应模型，按 controller/request/response 分层
|   +-- chat/                       # 聊天编排、RAG 注入、预算和遥测，按 domain/application 分层
|   +-- config/                     # Spring 配置属性
|   +-- console/                    # 管理和运维控制台视图服务，按 application/view 分层
|   +-- model/                      # 模型提供方抽象和实现
|   +-- orchestration/              # 多 Agent 路由、handoff、trace、Agent-as-Tool，按 domain/application 分层
|   +-- persistence/                # 通用持久化辅助代码
|   +-- production/                 # 持久化状态、运行时防护、遥测、快照，按 config/health/state/budget/telemetry/snapshot/workspace/infrastructure 分层
|   +-- rag/                        # 知识源、切片、检索、引用、反馈，按 domain/application/retrieval/persistence 分层
|   +-- release/                    # 发布验收和阶段门禁
|   +-- runtime/                    # 租户/用户/Agent/会话运行时上下文映射
|   +-- security/                   # 身份、授权、脱敏、Prompt 安全、Skill 治理，按 domain/application/persistence 分层
|   +-- session/                    # 聊天消息和会话存储，按 domain/persistence 分层
|   +-- tooling/                    # 工具注册、执行、权限、幂等、审计，按 domain/application/execution/audit/persistence 分层
+-- src/main/resources
|   +-- application.yml             # 默认本地配置
|   +-- application-development.yml
|   +-- application-production.yml
|   +-- application-test.yml
|   +-- db/migration/               # Flyway 公共迁移
|   +-- db/vendor-migration/        # Flyway 数据库专属迁移，如 H2/MySQL 注释脚本
+-- src/test/java/com/harnessagent   # 后端测试，按生产包结构组织
+-- web/                            # React/Vite 控制台
+-- docs/                           # 运维和发布文档
+-- openspec/                       # 规格驱动的变更 proposal、design 和 tasks
```

## 环境准备

### 前置条件

- JDK 17 或更高版本，并确认 Maven 使用同一个 JDK。
- Maven 3.9 或兼容版本。
- `web/` 前端需要 Node.js 和 npm。
- 可选：测试 DashScope 模型提供方时需要配置 `DASHSCOPE_API_KEY`。默认模型提供方是本地 `echo`。

检查后端工具链：

```bash
java -version
mvn -version
```

如果 Maven 显示 Java 8，需要先切换到 JDK 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

macOS 上使用 Homebrew JDK 的兜底路径：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

安装前端依赖：

```bash
cd web
npm install
```

## 开发流程

### 后端

在仓库根目录启动后端：

```bash
mvn spring-boot:run
```

默认 API 端口是 `8080`。本地默认使用 H2 和 `echo` 模型提供方，基础 smoke test 不需要外部模型 key。

常用 smoke check：

```bash
curl -i http://localhost:8080/api/release/scenario
curl -s http://localhost:8080/api/release/phase-gates
```

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

流式聊天示例：

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "tenant-a",
    "userId": "user-a",
    "agentId": "enterprise-assistant",
    "sessionId": "session-stream-a",
    "message": "请用三句话介绍平台能力"
  }'
```

### 前端

在 `web/` 目录启动控制台：

```bash
cd web
npm run dev
```

Vite 会把 `/api/**` 代理到 `http://localhost:8080`，所以做真实联调时需要保持后端运行。

### OpenSpec

仓库使用 `openspec/changes/` 下的规格驱动变更文档。对于非平凡的产品或行为变更：

- 修改实现前先阅读当前变更的 `proposal.md`、`design.md` 和 `tasks.md`。
- 实现必须对齐已接受的范围。不要在没有更新变更文档的情况下扩大产品面。
- 需要时运行规格校验：

```bash
openspec validate enterprise-agent-platform
```

## 测试说明

### 后端测试

在仓库根目录运行全部后端测试：

```bash
mvn test
```

运行单个后端测试类：

```bash
mvn test -Dtest=ChatServiceTest
```

后端测试应放在 `src/test/java/com/harnessagent` 下，与被修改的生产包对应。沿用现有的 JUnit 5、AssertJ、Mockito 和 Reactor Test 写法。

### 前端测试

在 `web/` 目录运行：

```bash
npm run test:unit
npm run test:browser
npm run build
```

组件和 API 逻辑使用 Vitest，浏览器流程使用 Playwright。当前浏览器测试会 mock 后端 API 响应，覆盖聊天、RAG 状态、管理、运维、审计和响应式视图。

### 发布前检查

发布前按变更范围运行相关检查：

```bash
mvn test
cd web && npm run test:unit && npm run test:browser && npm run build
```

仅后端变更不强制运行前端检查，除非 API 契约或控制台行为受影响。仅前端变更应运行前端检查；只有当前端修改影响 API 假设时才需要补跑后端测试。

## 代码规范

### Java

- 目标版本是 Java 17。不要引入超过 Maven `release` 配置的语言特性。
- 保持包边界清晰。API 模型和控制器放在 `api`，业务编排放在对应 service 包，持久化细节放在 store 类中。
- 新增或移动类时沿用当前分层：`domain` 放纯模型和值对象，`application` 放用例服务，`persistence` 放 store 接口和实现，`infrastructure` 放外部系统或运行时实现。
- Spring 服务优先使用构造器注入。
- 在现有包已经使用不可变 request/result 类型时，继续保持这种风格。
- 请求形态的非法输入使用 `IllegalArgumentException`；状态或策略拒绝使用 `IllegalStateException`。`ApiExceptionHandler` 会把它们映射为 HTTP 响应。
- 服务命令和持久化 key 中必须显式保留 `tenantId`、`userId`、`agentId`、`sessionId`。
- 修改受治理流程时，不要绕过 `RuntimeContextFactory`、`AuthorizationService`、`DataPermissionService`、`PromptInjectionGuard`、`BudgetLimiter` 或审计服务。
- 密钥必须来自环境变量或 Spring 配置占位符。不要硬编码 API key、token、密码或租户凭据。
- 新增日志必须使用 SLF4J 参数化日志和 allowlist 字段。可以记录 `tenantId`、`agentId`、状态、原因码、数量和耗时；`userId`、`sessionId`、`idempotencyKey` 等外部输入需 hash 或摘要；不要记录原始 prompt、query、工具参数、工具结果、DSN、Redis URI、snapshot 内容或 workspace 文件内容。

### 企业 Agent 约束

- 保持运行时隔离：
  - `tenantId`、`userId`、`agentId`、`sessionId` 必须贯穿聊天、RAG、工具、遥测、审计和生产状态链路。
  - 跨租户访问默认拒绝。
- RAG 回答必须遵守数据权限。没有可访问证据时，保留无答案行为，不要编造引用。
- 工具执行必须同时保留策略检查、参数校验、高风险确认/审批、幂等和审计记录。
- 高风险操作应可回滚，或在发布回滚文档中明确覆盖。
- 日志、遥测和审计视图中的敏感数据必须脱敏。
- 生产运行时变更必须考虑多副本状态、持久化存储、工作区策略、快照、预算、fallback 和超时。

### React/TypeScript

- 控制台是工作型运维界面，应保持紧凑、清晰、可扫描，不要做成营销页。
- 沿用 `web/src/views` 的视图结构，以及 `web/src/api`、`web/src/components`、`web/src/lib` 中的共享能力。
- API 形态数据使用 `web/src/api/types.ts` 中的 TypeScript 类型。
- 本地身份面板的行为要保持显式。生产身份和角色必须来自可信基础设施，不能来自任意浏览器状态。
- 样式沿用 `web/src/styles.css` 的现有约定。修改行为时避免无关视觉重写。
- 当前 UI 使用图标的地方优先使用 `lucide-react`。

### 注释和文档

- 只为不明显的逻辑、策略判断或复杂的 Agent/RAG/工具交互添加注释。
- 修改运维行为、发布门禁、运行时配置或回滚预期时，同步更新 `docs/`。
- 修改 Flyway 路径、数据库 schema、表字段或 metadata comments 时，同步更新 `docs/durable-persistence-schema.md` 和 `docs/release-readiness.md`。公共 DDL 放 `db/migration`，数据库专属脚本放 `db/vendor-migration/{vendor}`，避免被公共 migration root 递归加载。
- 修改前端启动、校验、代理或 UI 限制时，同步更新 `web/README.md`。

## 构建和部署说明

后端打包：

```bash
mvn package
```

前端打包：

```bash
cd web
npm run build
```

生产 profile 需要完整的持久化基础设施接线。修改生产默认值前，先阅读 `docs/release-readiness.md` 和 `application-production.yml`。

重要生产环境变量包括：

- `HARNESS_AGENT_MYSQL_DSN`
- `HARNESS_AGENT_DB_USERNAME`
- `HARNESS_AGENT_DB_PASSWORD`
- `HARNESS_AGENT_DURABLE_STATE_WIRED=true`
- `HARNESS_AGENT_DURABLE_TELEMETRY_ENABLED=true` 或 `HARNESS_AGENT_OTEL_ENABLED=true`
- 启用 Redis-backed state 或 budget counter 时需要 `HARNESS_AGENT_REDIS_URI`

除非经过评审的恢复方案明确要求，否则回滚时不要删除审计、遥测、幂等、会话、AgentScope state 或快照数据。

## 常见问题

### Java 版本不匹配

典型现象包括 `invalid flag: --release`、`class file version 61.0` 或 Spring Boot Maven 插件加载失败。需要确认 `java -version` 和 `mvn -version` 都使用 JDK 17。

### 后端端口被占用

后端默认使用 `8080`。可以通过 Spring 配置修改 `server.port`，也可以先停止已有进程再启动应用。

### 前端无法访问 API

启动或使用 Vite 控制台前，先确认后端运行在 `localhost:8080`。前端开发服务器预期把 `/api/**` 代理到这个后端。

### 缺少模型凭据

默认 `echo` provider 不需要任何凭据。只有测试 DashScope provider 或相关配置时才需要设置 `DASHSCOPE_API_KEY`。

## Agent 工作规则

- 修改前先阅读相关包和测试。避免做任务不需要的宽泛重构。
- 除非任务明确要求变更公共 API 契约，并同步更新测试和文档，否则保留现有契约。
- 行为变更需要添加聚焦测试，尤其是隔离、授权、RAG 无答案/引用、工具治理、持久化和 UI 工作流。
- 不要提交生成产物、构建输出或本地运行时状态。
- 优先选择最小但完整的改动，同时保持企业安全模型不被削弱。
