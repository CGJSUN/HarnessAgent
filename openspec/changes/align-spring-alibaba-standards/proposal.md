## Why

当前后端包结构以一层业务包为主，部分包内混放 controller、DTO、领域模型、应用服务、基础设施实现、配置、健康检查和视图模型，长期维护时职责边界不够清晰。与此同时，关键治理链路缺少必要策略注释和应用日志，数据库 schema 也缺少表/字段用途备注，不利于生产排障、交接和按 Spring/阿里 Java 开发规范持续演进。

## What Changes

- 建立符合 Spring Boot 分层实践和阿里 Java 开发规范取向的包结构治理规则，明确 controller、request/response DTO、application service、domain model、repository/store、config、infrastructure、health 等代码放置边界。
- 分阶段整理当前后端包结构，优先处理 `production`、`api`、`console`、`tooling`、`rag` 等职责过粗的包，移动不合理位置的类，并同步测试包路径和 imports。
- 为复杂业务链路补充必要注释，只解释非显而易见的策略、安全边界、状态保存时机、幂等/审计/降级取舍，避免机械注释。
- 在关键拒绝、降级、治理操作、工具执行异常、生产 readiness 阻断、快照/状态失败等路径补充 SLF4J 参数化日志。
- 建立日志脱敏边界，禁止输出原始 prompt、query、工具参数、工具结果、secret、token、DSN、Redis URI、Agent state、snapshot 内容和 workspace 文件内容。
- 为现有持久化 schema 补充表级和字段级用途说明：新增后续 migration 或 vendor-aware 注释脚本，不直接修改已应用的 `V1__durable_persistence.sql`。
- 在文档中补充包结构说明、日志/注释规范、数据库字段字典和发布/回滚时的 schema 备注要求。
- 保持行为等价：不改变 REST URL、请求/响应字段、SSE 事件、数据库表名、业务治理链路、RAG 策略、工具执行语义、审计模型或遥测模型。

## Capabilities

### New Capabilities

- `spring-alibaba-package-structure`: 定义后端包结构、职责边界、类移动原则、测试路径同步和禁止破坏的 API/状态/治理约束。
- `code-comments-logging`: 定义必要注释与必要日志的范围、SLF4J 使用规范、日志级别、敏感数据脱敏和关键治理链路观测要求。
- `database-metadata-comments`: 定义数据库表/字段注释、字段字典、JSON 字段备注、H2/MySQL 兼容策略和 migration 验收要求。

### Modified Capabilities

- 无。当前仓库没有已归档到 `openspec/specs/` 的 accepted specs；本变更新增可维护性与规范治理能力，不改变已有业务能力要求。

## Impact

- 影响后端 Java 包结构和测试包 imports，但不改变 Spring Boot 扫描根包、Maven 坐标或运行时 API 契约。
- 影响 `src/main/java/com/harnessagent` 下的包组织、关键类注释和日志点，重点涉及 `production`、`api`、`console`、`tooling`、`rag`、`chat`、`security`、`agent`、`orchestration`。
- 影响 `src/main/resources/db/migration`，需要新增数据库注释相关 migration 或 vendor-aware 脚本，并确保 H2 测试路径可继续执行。
- 影响 `docs/start.md`、`docs/release-readiness.md`、AGENTS/项目上下文类文档中关于包结构、日志注释、DB schema 字段字典和发布验收的说明。
- 不引入 Lombok、MapStruct、新日志框架、多 Maven 模块、微服务拆分、新 RAG 引擎、真实 MCP client、对象存储 snapshot 或业务 schema 重构。
