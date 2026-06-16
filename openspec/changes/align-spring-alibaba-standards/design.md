## Context

当前后端代码位于 `com.harnessagent` 根包下，整体仍保持 Spring Boot 单体应用形态，但多数业务包只做了一层平铺划分。`production` 同时承载生产配置、状态存储、预算、遥测、快照、工作区、健康检查和 JDBC/Redis/InMemory 实现；`api` 混放 controller、request、response 和异常模型；`console` 聚合用户视图、管理视图、审计视图和运维指标；`tooling`、`rag`、`security` 也把领域模型、服务、策略和持久化实现放在同一层。随着企业 Agent 治理能力增加，这种结构会削弱包边界和代码可发现性。

现有应用代码基本没有 SLF4J 应用日志，关键拒绝、降级、工具治理、生产 readiness、状态/快照失败路径主要依赖返回值、异常和遥测记录。注释也应保持克制，只补充复杂策略、治理顺序和持久化/降级取舍，避免机械注释。

数据库当前只有 `V1__durable_persistence.sql`，创建 14 张持久化表，但没有表级或字段级注释。部分测试直接加载 V1，另有 `JdbcStoreTestSupport` 手写 H2 DDL 覆盖 RAG、Tool、Telemetry 局部表结构。V1 已作为 Flyway 历史 migration 存在，不能通过修改 V1 来补注释，否则会造成 checksum 变化和已部署环境校验失败。

本变更必须保持 MVP 行为等价：不改变 REST URL、请求/响应字段、SSE 事件、数据库表名、配置 key、租户/用户/Agent/会话隔离链路、RAG 权限过滤、工具审批/幂等/审计、预算限制和遥测语义。

## Goals / Non-Goals

**Goals:**

- 建立符合 Spring Boot 分层实践和阿里 Java 开发规范取向的包结构规则，让 controller、DTO、应用服务、领域模型、策略、store 接口、基础设施实现、配置和健康检查边界清晰。
- 分阶段整理当前包结构，优先降低 `production`、`api`、`console`、`tooling`、`rag` 的维护成本，并同步测试包路径和 imports。
- 在复杂治理链路补充必要注释，解释非显而易见的策略顺序、安全边界、状态保存时机、幂等/审计/降级取舍。
- 在关键拒绝、降级、治理操作、执行异常、readiness 阻断、状态/快照失败路径补充 SLF4J 参数化日志。
- 明确日志脱敏边界，避免日志成为 prompt、工具参数、工具结果、凭据、状态和工作区内容的泄露通道。
- 通过后续 migration 或 vendor-aware 脚本补充数据库表/字段注释，并在文档中建立字段字典。

**Non-Goals:**

- 不拆分 Maven 多模块，不迁移为微服务，不更换 Spring Boot 扫描根包。
- 不引入 Lombok、MapStruct、新日志框架或新的代码生成链路。
- 不改变 API 契约、SSE 契约、异常映射、DB 表名、索引语义或业务数据模型。
- 不重写 RAG 算法、工具执行模型、权限模型、审计模型、AgentScope runtime 或生产持久化能力。
- 不在包结构整理中顺手修改控制台视觉、前端 API 假设或发布门禁语义。

## Decisions

### 1. 保持单体根包，按领域内分层逐步拆子包

保留 `com.harnessagent` 作为 Spring Boot 扫描根包，避免影响 `@Service`、`@Component`、`@RestController`、`@ConfigurationProperties` 自动发现。每个业务域先在现有根包下增加清晰子包，而不是改 Maven 坐标或拆多模块。

建议目标边界：

- `api.controller`：REST controller。
- `api.request` / `api.response`：入参和出参 DTO。
- `api.mapper`：仅当领域对象和 API 形态需要隔离时引入。
- `production.config`、`production.health`、`production.state`、`production.budget`、`production.telemetry`、`production.snapshot`、`production.workspace`、`production.infrastructure`：拆开生产运行时职责。
- `console.application` / `console.view`：控制台聚合服务和只读视图模型。
- `tooling.domain`、`tooling.application`、`tooling.execution`、`tooling.persistence`、`tooling.audit`：区分工具定义、执行、持久化和审计。
- `rag.domain`、`rag.application`、`rag.retrieval`、`rag.persistence`：区分知识源/切片、检索策略和存储实现。
- `security.domain`、`security.application`、`security.persistence`：后置处理安全域，避免一次性移动导致授权链路回归。

替代方案是直接拆 Maven 多模块或做完整 DDD 分层。该方案会引入构建、包可见性和依赖治理成本，超出本次“包结构清晰化”的目标，因此不采用。

### 2. 包结构整理采用低风险分阶段迁移

第一阶段只移动低风险类并更新 imports，包括 controller/DTO/view/store 实现类、生产运行时的 health/config/state/budget/telemetry/snapshot/workspace 类。第二阶段治理持久化边界，将 `Jdbc*Store`、`Redis*Store`、`InMemory*Store` 放入对应 `persistence` 或 `infrastructure` 子包，接口保留在应用服务可依赖的位置。第三阶段再整理 `ChatService`、`ToolService`、`KnowledgeService`、`OrchestrationService` 周边的领域模型和策略对象。第四阶段才考虑 API response DTO 与 mapper 隔离，并且必须以契约测试保护 JSON/SSE 形态。

迁移期间禁止把 `production` 变成公共工具包。跨域依赖应依赖接口、服务或明确的能力模型，不让生产健康检查直接耦合其他域的 JDBC 实现细节。若必须保留现有判断逻辑，先移动位置并保持行为，后续再通过小接口或 capability 模型解除反向依赖。

替代方案是一次性移动所有类。该方案会造成 imports 大面积变化、测试失败定位困难，也容易混入行为变更，因此不采用。

### 3. API 契约和治理调用顺序优先于包名整洁

短期允许 controller 继续返回现有领域对象，避免为了包结构整理而改变 JSON 输出。只有在 specs/tasks 明确要求并补齐 controller 契约测试后，才引入 response DTO 替换返回类型。

聊天、RAG、工具和生产运行时链路的调用顺序必须保持：

- 聊天请求先创建 `RuntimeContextScope`，再执行 prompt safety、budget、消息持久化、RAG、Agent runtime、遥测记录。
- RAG 必须保留租户和用户权限过滤，无可访问证据时保持无答案行为。
- 工具执行必须保留启用状态、租户归属、权限策略、参数校验、prompt safety、高风险确认/审批、幂等、执行、审计、遥测顺序。
- 生产 readiness 不能绕过 durable store、snapshot、telemetry、budget、workspace、fallback 和 timeout 约束。

替代方案是借包迁移拆分服务逻辑。该方案会让结构调整和行为调整耦合，难以验证，因此本变更仅允许在测试覆盖明确时做小范围方法提取。

### 4. 日志使用 Spring Boot 默认 SLF4J，补关键事件而非流水账

不引入新日志框架，使用 Spring Boot 默认 logging 与 SLF4J 参数化日志。日志补充以“生产排障和治理审计辅助”为目标，不替代安全审计表、遥测表或业务返回值。

建议日志级别：

- `INFO`：工具注册/启停、高风险工具进入 pending、生产 readiness 阻断摘要、快照创建成功等低频治理事件。
- `WARN`：prompt safety 拒绝、预算超限、RAG 无可访问证据、工具权限拒绝、幂等冲突、fallback 触发、健康检查失败。
- `ERROR`：模型调用失败、工具执行异常、状态/快照持久化失败、迁移或生产存储不可用等需要人工介入的问题。
- `DEBUG`：候选数、命中数、耗时、策略分支等排障细节，但仍不得包含敏感原文。

日志字段采用 allowlist：组件名、状态、原因码、耗时、数量、布尔结果、`tenantId`、`agentId`、`toolId`、`sourceId` 等受控标识。`userId`、`sessionId`、`idempotencyKey` 可能包含 PII 或外部输入，日志中如需定位只能记录哈希或后缀摘要。不得记录原始 prompt、query、RAG chunk 内容、知识源标题、反馈评论、工具参数、工具输出、参数 fingerprint、API key、token、password、DSN、Redis URI、Agent state、snapshot 内容、workspace 文件内容和完整本地路径。

禁止使用 `System.out`、`System.err`、`printStackTrace` 或字符串拼接 dump DTO/map/异常消息。异常日志优先记录异常类型、脱敏后的原因码和受控上下文字段；只有基础设施异常需要堆栈时才记录 throwable，且调用点必须确保异常消息不携带 prompt、工具参数、state、snapshot 或 URI。

替代方案是大范围加入方法入口/出口日志。该方案噪声高且增加泄露风险，因此不采用。

### 5. 注释只解释策略和约束，不复述代码

新增注释集中在不明显但重要的位置，例如 RAG no-answer 的安全含义、工具审批/幂等/审计顺序、状态保存时机、fallback/timeout 的取舍、生产 readiness 为什么 fail-fast、数据库 JSON 字段为什么保存为文本。简单 getter、构造器、变量赋值和显而易见的条件分支不加注释。

替代方案是按类或按方法补齐 JavaDoc。该方案容易产生低价值注释并增加维护成本，因此不采用。

### 6. 数据库注释通过新增 vendor-aware migration 补齐

不修改 `V1__durable_persistence.sql`。新增 V2 注释 migration 或 vendor-aware 脚本，覆盖 14 张表和全部字段用途。生产 MySQL 使用 MySQL 语法补充表/字段注释；H2 使用 `COMMENT ON TABLE/COLUMN` 或 no-op 兼容脚本，确保本地和测试 Flyway 可执行。

推荐将 Flyway locations 扩展为公共 migration 加 vendor-specific migration，例如 `classpath:db/migration,classpath:db/vendor-migration/{vendor}`。Vendor-specific 目录不要放在公共 migration root 下，避免 Flyway 递归扫描时同时加载 H2 和 MySQL 的同版本 migration。MySQL 字段注释若使用 `ALTER TABLE ... MODIFY COLUMN ... COMMENT`，必须完整保留原字段类型、长度、NULL 约束和默认行为，避免注释 migration 意外改变 schema。

字段注释必须明确用途和隔离语义，尤其是：

- `tenant_id`、`user_id`、`agent_id`、`session_id` 的隔离和查询维度。
- `*_json`、`attributes_json`、`details_json` 是序列化 JSON 文本，不是 native JSON。
- `risk_level`、`mutating`、`enabled`、`approval_id`、`idempotency_key` 等工具治理字段。
- `allowed_*_json`、`permitted_count`、`failure_reason` 等 RAG 权限和无答案字段。
- `state_key`、`scope`、`state_value`、`counter_key`、`location`、`content` 等生产状态、预算和快照字段。

替代方案是只在 docs 里写字段字典。该方案不能改善数据库元数据，不满足运维和 DBA 查看需求，因此不采用。另一个替代方案是在 V1 直接追加 COMMENT，该方案会破坏 Flyway checksum，因此不采用。

## Risks / Trade-offs

- [Risk] 包移动导致 Spring Bean 扫描、构造器注入或测试 imports 失效 → Mitigation：保持根包 `com.harnessagent` 不变，按阶段移动，每阶段运行对应单测，最终运行 `mvn test`。
- [Risk] API controller 返回领域对象，包迁移时可能诱发 JSON 契约变化 → Mitigation：第一阶段只改包名/import；若引入 response DTO，必须补 controller 契约测试和前端类型核对。
- [Risk] `ChatService` 流式路径同时涉及持久化、RAG no-answer、SSE citations 和 telemetry → Mitigation：移动前后覆盖非流式、流式、RAG no-answer、budget reject 和模型失败测试。
- [Risk] 工具执行链路顺序被整理代码时破坏 → Mitigation：`ToolService` 先只做子包归类；任何逻辑拆分必须保留 preflight、审批、幂等、执行、审计、遥测顺序，并补工具治理测试。
- [Risk] 日志补充泄露敏感数据 → Mitigation：集中定义日志脱敏清单；日志评审检查不得出现 prompt、query、工具输入输出、secret、token、DSN、Redis URI、state、snapshot、workspace 内容。
- [Risk] MySQL 字段注释 migration 使用 `MODIFY COLUMN` 时误改字段定义 → Mitigation：从 V1 逐字段复制完整定义；新增 migration 测试和人工 review；注释 migration 不夹带类型、索引、约束调整。
- [Risk] H2 与 MySQL 注释语法不兼容 → Mitigation：使用 vendor-aware locations；H2 走 `COMMENT ON` 或 no-op，MySQL 走 MySQL 注释语法；测试至少验证 H2 Flyway 路径。
- [Risk] 修改 V1 造成已部署环境 Flyway 校验失败 → Mitigation：V1 保持不可变；所有注释通过 V2+ roll-forward。
- [Risk] 文档字段字典和真实 migration 注释不一致 → Mitigation：tasks 要求同一批次更新 docs 和 migration，并通过审阅检查表/字段覆盖率。

## Migration Plan

1. 先落地 OpenSpec specs 和 tasks，明确每个 capability 的验收标准。
2. 新增目标包结构，并按低风险类别移动类；每次移动后更新测试包和 imports，保持编译通过。
3. 为关键治理链路补必要注释和日志，优先处理 `ChatService`、`ToolService`、`KnowledgeService`、生产 runtime/health/snapshot/state 相关服务。
4. 新增 DB 注释 migration 或 vendor-aware migration 目录；H2 路径用于本地/测试兼容，MySQL 路径用于生产元数据。
5. 更新 `docs/start.md`、`docs/release-readiness.md`、项目上下文/AGENTS 类文档，说明包结构、日志脱敏、注释原则、DB 字段字典和发布验收要求。
6. 验证：运行受影响单测，最终运行 `mvn test`；DB 注释验证至少覆盖 H2 Flyway 可执行，MySQL 环境通过 `information_schema.tables` 和 `information_schema.columns` 检查表/字段注释覆盖率。

回滚策略：

- 包结构和日志/注释变更可随应用版本回滚，但不得回滚到绕过治理链路的实现。
- DB V2 注释 migration 一旦发布，不删除、不改写历史 migration。需要修正注释时通过 V3 roll-forward；不 drop 表、不改字段、不清数据。
- 如 migration 半失败，先记录 Flyway 状态和 `information_schema` 注释差异，再按数据库变更流程处理，必要时 DBA 审批后执行 `repair`，不能直接改历史 migration。

## Open Questions

- 是否要求 H2 migration 也真实写入表/字段 remarks，还是只要求 no-op 保证本地 Flyway 可执行？
- MySQL 注释 migration 是否由应用仓库直接维护，还是拆为 DBA 审批脚本并在 docs 中引用执行路径？
- API 层是否在本变更内引入 response DTO 隔离，还是只整理 controller/request/response 子包并保持返回类型不变？
- `DurablePersistenceHealthService` 对各 JDBC store 实现的 wiring 判断，是否在本变更中抽象为 store capability 接口，还是只移动包位置并保留现有逻辑？
