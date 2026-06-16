## 1. 基线盘点与契约保护

- [x] 1.1 生成当前 `src/main/java/com/harnessagent` 包结构到目标包结构的类迁移清单，标注 API、console、production、tooling、rag、security、chat、orchestration、agent 的目标职责边界。
- [x] 1.2 确认 `HarnessAgentApplication` 保持在 `com.harnessagent` 根包，且 `@SpringBootApplication` 不新增自定义扫描根。
- [x] 1.3 盘点现有 REST URL、请求/响应字段和 `/api/chat/stream` SSE 事件形态，补齐或确认覆盖 API 契约的测试。
- [x] 1.4 盘点聊天、RAG、工具、生产 readiness 的关键治理调用顺序，列出重构后必须保持的测试用例。
- [x] 1.5 确认 `pom.xml` 当前无 Lombok、MapStruct、多 Maven 模块或新日志框架依赖，并将该检查纳入最终验证。

## 2. 包结构整理

- [x] 2.1 创建 API 分层子包并移动 controller、request DTO、response DTO、错误响应和 SSE 响应类，保持 REST 路径和 JSON 契约不变。
- [x] 2.2 创建 `console.application` 和 `console.view` 等控制台子包，移动控制台聚合服务、筛选条件和只读视图模型。
- [x] 2.3 拆分 `production` 为 config、health、state、budget、telemetry、snapshot、workspace、infrastructure 等职责子包，保持 active bean 和 readiness 行为不变。
- [x] 2.4 移动 `tooling`、`rag`、`security`、`session`、`orchestration` 中的 enum、record、纯模型和策略值对象，禁止改业务方法、序列化字段名和 API 返回字段。
- [x] 2.5 将 `tooling` 中的领域模型、应用服务、执行器、审计模型、store 接口和 JDBC/InMemory 实现移动到明确子包，保持工具治理链路顺序不变。
- [x] 2.6 将 `rag` 中的知识源/切片模型、检索策略、应用服务、store 接口和 JDBC/InMemory 实现移动到明确子包，保持权限过滤、citation 和 no-answer 行为不变。
- [x] 2.7 谨慎整理 `security`、`chat`、`agent` 和 `orchestration` 周边类，只做低风险包归类和 imports 更新，不借机重写治理流程。
- [x] 2.8 收敛 `production` 跨域依赖，将 health 对 session、knowledge、tool、audit、telemetry 具体实现的判断抽象为明确 capability 或接口，避免 `production` 直接承载其他域实现细节。
- [x] 2.9 同步更新 `src/test/java/com/harnessagent` 下测试包、imports 和测试辅助类引用，确保测试不依赖旧包路径。
- [x] 2.10 运行受影响的后端测试，至少覆盖 API 契约、Chat/RAG、Tool、Production runtime、JDBC store 和 Orchestration 相关测试。

## 3. 注释与日志实现

- [x] 3.1 增加安全日志标识处理能力，用于构造 allowlist 日志上下文、归一化原因码，并对 `userId`、`sessionId`、`idempotencyKey` 等可能含 PII 或外部输入的字段生成稳定 hash 或摘要，补单测覆盖稳定摘要和空值处理。
- [x] 3.2 在 Chat/RAG 链路补充必要注释，说明 prompt safety、budget、消息持久化、RAG、模型调用、citation 和 no-answer 的治理顺序。
- [x] 3.3 在 Tool 链路补充必要注释，说明 preflight、权限、参数白名单、Prompt 注入检查、高风险审批、幂等、执行、审计和 telemetry 的顺序约束。
- [x] 3.4 在 Production runtime、Workspace snapshot 和 Orchestration 关键路径补充必要注释，说明 fail-fast、状态隔离、snapshot 越权保护、fallback/timeout 和 context boundary 取舍。
- [x] 3.5 在 Chat/RAG/Security 关键拒绝和失败路径补充 SLF4J 参数化日志，覆盖预算拒绝、Prompt 安全拒绝、RAG 无可访问证据、模型失败、流式异常、跨租户/RBAC 拒绝、权限过滤摘要和 Skill 生命周期事件。
- [x] 3.6 在 KnowledgeService 补充 RAG 生命周期日志，覆盖知识源注册/撤销/删除、文档切片入库数量、候选数、权限过滤后数量和无可访问证据，确保日志不含 query、chunk、title 或 feedback comment。
- [x] 3.7 在 Tool 关键路径补充 SLF4J 参数化日志，覆盖未知工具、租户不匹配、权限拒绝、参数拒绝、高风险 pending、幂等冲突/复用、执行失败和超时。
- [x] 3.8 在 Production/Orchestration 关键路径补充 SLF4J 参数化日志，覆盖 readiness 阻断、durable health 失败、state/snapshot 失败、workspace 策略拒绝、fallback/timeout、路由成功/失败、人工升级、handoff 或 context boundary 生效。
- [x] 3.9 增加或调整日志测试，使用 Logback ListAppender 或等价方式断言关键事件级别和字段，并验证敏感 prompt、query、工具参数、工具结果、DSN、Redis URI、snapshot/workspace 内容不会出现在新增日志中。
- [x] 3.10 运行静态扫描，确认生产代码没有 `System.out`、`System.err`、`printStackTrace`、`java.util.logging`、字符串拼接日志或 `String.format` 日志。

## 4. 数据库表和字段注释

- [x] 4.1 基于 `V1__durable_persistence.sql` 建立 14 张表的字段字典草稿，覆盖表用途、字段用途、隔离字段、JSON 文本字段、治理字段、RAG 字段和生产状态字段。
- [x] 4.2 保持 `V1__durable_persistence.sql` 无改动，新增 V2+ 注释 migration 或 vendor-aware migration 目录。
- [x] 4.3 配置 Flyway migration location 支持公共 migration 与 vendor-specific migration 分流，同步 `application.yml`、`application-production.yml` 和生产 schema 文档中的 migration-location，确保 H2 和 MySQL 使用各自兼容的注释脚本。
- [x] 4.4 编写 H2 注释 migration，使用 H2 兼容的 `COMMENT ON TABLE/COLUMN` 或明确 no-op，并保证本地/测试 Flyway 路径可执行。
- [x] 4.5 编写 MySQL 注释 migration，覆盖 14 张表和全部现有字段；如使用 `MODIFY COLUMN ... COMMENT`，完整保留 V1 字段类型、长度、NULL 约束和默认语义。
- [x] 4.6 增加 metadata 验证测试或验收脚本，确认 H2 路径可执行，并提供 MySQL `information_schema.tables` / `information_schema.columns` 注释覆盖率查询 SQL 或文档片段。
- [x] 4.7 确认现有直接加载 V1 的 H2 JDBC store 测试和 `JdbcStoreTestSupport` 手写 DDL 局部测试不因新增注释 migration 失效。
- [x] 4.8 审查注释内容，确认不包含原始 prompt、query、工具输入输出、token、password、DSN、Redis URI、snapshot 内容或 workspace 文件内容。

## 5. 文档同步

- [x] 5.1 更新项目结构文档，说明新的 API、console、production、tooling、rag、security、chat、agent、orchestration 包职责边界。
- [x] 5.2 更新 `docs/start.md` 或相关持久化文档，加入 14 张表的表用途、字段用途、关键索引维度和 JSON 文本字段说明。
- [x] 5.3 更新 `docs/release-readiness.md`，说明 DB 注释 migration 的发布验收、MySQL metadata 查询、H2 兼容路径和 roll-forward 回滚策略。
- [x] 5.4 更新 AGENTS/项目上下文类文档中的开发规范，说明包结构、必要注释、日志 allowlist、敏感字段脱敏和禁止事项。
- [x] 5.5 检查文档中关于包结构、日志、注释、DB schema 和生产发布的表述，确保与 OpenSpec specs 一致。

## 6. 验证与收敛

- [x] 6.1 运行 `rg` 检查生产代码中无 Lombok、MapStruct、新日志框架、`System.out`、`System.err`、`printStackTrace`、`java.util.logging`、字符串拼接日志或 `String.format` 日志。
- [x] 6.2 运行 API、Chat/RAG、Tool、Production runtime、JDBC store、Orchestration 相关测试，确认包迁移和日志注释不改变业务行为。
- [x] 6.3 运行 `mvn test`，确认后端全量测试通过。
- [x] 6.4 运行 `openspec validate align-spring-alibaba-standards`，确认 OpenSpec artifact 仍有效。
- [x] 6.5 查看 `openspec status --change align-spring-alibaba-standards`，确认 proposal、design、specs 和 tasks 均已完成。
- [x] 6.6 汇总未解决的 MySQL 环境实测、DBA 审批或 H2 remarks 读取限制，明确是否需要后续独立 OpenSpec 变更。
