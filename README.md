# HarnessAgent

HarnessAgent 当前目标是个人版完整 Agent 应用：以单个 owner 的工作区、会话、记忆、工具授权和多 Agent 协作为中心，基于 Spring Boot、React/Vite 和 AgentScope Java v2/Harness 构建可长期运行的个人 Agent。

早期受治理平台能力已经退出默认产品路径，只在迁移盘点、历史数据转换或开发诊断中说明。个人版仍保留最小安全要求：工作区隔离、路径校验、高风险工具确认、沙箱、脱敏日志、幂等和可撤销记录。

## 目标范围

- 个人聊天 API 和 Web 工作台，支持非流式、流式事件、会话恢复和执行摘要。
- 个人工作区，覆盖文件、计划、快照、上下文压缩、channel 和长期任务状态。
- 个人记忆与 RAG，覆盖本地知识源、引用来源、无答案策略、删除和导出。
- 个人工具和技能，覆盖 schema 校验、授权三态、Human-in-the-loop、幂等和最小审计。
- 个人多 Agent 编排，覆盖 supervisor、subagent、handoff、Agent-as-Tool 和 trace。
- AgentScope Java v2 官方文档页面级覆盖矩阵，作为“完整覆盖”声明的验收依据。

## 非目标和遗留兼容

- 组织治理、集中报表和阶段准入不是个人版核心验收目标；相关历史说明只出现在迁移和诊断材料中。
- 旧请求形态和旧诊断入口若短期保留，必须默认关闭或默认隐藏，个人工作台不得调用它们作为主流程。
- 默认状态 key、请求体、前端类型和文档示例均使用 owner、agent、session 和 workspace；旧 key 只用于一次性迁移或备份恢复验证，后续写入必须使用 owner key。

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

默认 API 端口是 `8080`，Vite 会把 `/api/**` 代理到后端。默认 API 和 Web 工作台使用 owner、Agent、session 和 workspace 个人语义；兼容层只用于迁移或诊断。

## 文档入口

- [docs/start.md](docs/start.md)：个人版目标、架构和本地使用指南。
- 迁移盘点文档：历史概念迁移清单和处理规则。
- [docs/agentscope-java-v2-coverage.md](docs/agentscope-java-v2-coverage.md)：AgentScope Java v2 官方文档覆盖矩阵。
- [docs/release-readiness.md](docs/release-readiness.md)：个人版发布验收、迁移备份和回滚处理。
- [docs/durable-persistence-schema.md](docs/durable-persistence-schema.md)：持久化 schema 及个人版字段解释。
- [web/README.md](web/README.md)：个人工作台前端说明。
