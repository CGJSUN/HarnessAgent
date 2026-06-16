# HarnessAgent

HarnessAgent 当前目标是个人版完整 Agent 应用：以单个 owner 的工作区、会话、记忆、工具授权和多 Agent 协作为中心，基于 Spring Boot、React/Vite 和 AgentScope Java v2/Harness 构建可长期运行的个人 Agent。

早期仓库中存在企业平台 MVP 的租户、RBAC、管理后台、运营报表、审计报表和发布门禁概念。这些能力在个人版中只作为迁移期兼容层或开发诊断存在，不再是核心产品目标。个人版仍保留最小安全要求：工作区隔离、路径校验、高风险工具确认、沙箱、脱敏日志、幂等和可撤销记录。

## 目标范围

- 个人聊天 API 和 Web 工作台，支持非流式、流式事件、会话恢复和执行摘要。
- 个人工作区，覆盖文件、计划、快照、上下文压缩、channel 和长期任务状态。
- 个人记忆与 RAG，覆盖本地知识源、引用来源、无答案策略、删除和导出。
- 个人工具和技能，覆盖 schema 校验、授权三态、Human-in-the-loop、幂等和最小审计。
- 个人多 Agent 编排，覆盖 supervisor、subagent、handoff、Agent-as-Tool 和 trace。
- AgentScope Java v2 官方文档页面级覆盖矩阵，作为“完整覆盖”声明的验收依据。

## 非目标和遗留兼容

- 企业多租户治理、企业 RBAC、部门/角色数据权限、企业审计报表、运营后台和发布门禁不是个人版核心验收目标。
- 现有 `tenantId`、`roles`、`departments`、console admin/ops/audit 视图和 release gate API 可以保留作兼容或诊断，但默认个人流程不应要求用户理解这些概念。
- 内部状态 key 可继续保留 tenant/user/agent/session 字段；个人版默认把 tenant 映射为字面量 `personal`，把 user 映射为 owner，并派生 AgentScope 运行时用户 `personal:<owner>`。

## 技术栈

- Java 17、Spring Boot 3.3.x、Maven
- AgentScope Java v2 / Harness
- JUnit 5、AssertJ、Mockito、Reactor Test
- React 19、TypeScript、Vite、Vitest、Playwright
- 本地和测试默认使用 H2、内存或 local-json；生产接线以 MySQL/JDBC、可选 Redis 和快照存储为准

## 本地启动

后端：

```bash
mvn spring-boot:run
```

前端：

```bash
cd web
npm install
npm run dev
```

默认 API 端口是 `8080`，Vite 会把 `/api/**` 代理到后端。当前代码仍包含企业兼容字段；个人版 profile 和 API 语义按 `openspec/changes/build-personal-agentscope-agent` 后续实现任务逐步收敛。

## 文档入口

- [docs/start.md](docs/start.md)：个人版目标、架构和本地使用指南，保留企业遗留章节说明。
- [docs/enterprise-to-personal-migration-inventory.md](docs/enterprise-to-personal-migration-inventory.md)：企业概念迁移盘点和处理规则。
- [docs/agentscope-java-v2-coverage.md](docs/agentscope-java-v2-coverage.md)：AgentScope Java v2 官方文档覆盖矩阵。
- [docs/release-readiness.md](docs/release-readiness.md)：个人版发布验收和遗留 release gate 处理。
- [docs/durable-persistence-schema.md](docs/durable-persistence-schema.md)：持久化 schema 及个人版字段解释。
- [web/README.md](web/README.md)：个人工作台前端说明。
