## ADDED Requirements

### Requirement: Spring Boot 根包稳定
系统 SHALL 保持 `com.harnessagent` 作为 Spring Boot 应用根包和组件扫描边界，并 SHALL NOT 因包结构整理改变 Maven 坐标、启动类位置或应用扫描根。

#### Scenario: 应用入口保持稳定
- **WHEN** 检查 `HarnessAgentApplication`
- **THEN** 其包名 SHALL 仍为 `com.harnessagent`
- **AND** `@SpringBootApplication` SHALL NOT 指定新的扫描根包

#### Scenario: 移动 Spring Bean 类
- **WHEN** 服务类、控制器、配置属性类或持久化实现类被移动到新的子包
- **THEN** 这些类 SHALL 仍位于 `com.harnessagent` 根包之下
- **AND** Spring Boot SHALL 能继续扫描到对应 Bean

#### Scenario: 检查构建结构
- **WHEN** 包结构整理完成
- **THEN** 项目 SHALL 仍保持当前单 Maven 模块应用结构
- **AND** 变更 SHALL NOT 引入新的 Maven 多模块拆分

### Requirement: 分层包职责边界
系统 SHALL 按 Spring Boot 分层实践和阿里 Java 开发规范取向组织后端包，使 controller、request/response DTO、应用服务、领域模型、策略、store 接口、基础设施实现、配置和健康检查具备清晰放置边界。

#### Scenario: 整理 API 层
- **WHEN** API 层类被整理
- **THEN** REST controller SHALL 放入 controller 边界
- **AND** request/response DTO SHALL 放入对应 DTO 边界
- **AND** 异常处理类 SHALL 保持在 API 适配层边界内

#### Scenario: 整理业务域
- **WHEN** `tooling`、`rag`、`security` 或 `orchestration` 域被整理
- **THEN** 应用服务、领域模型、策略对象、执行器和持久化实现 SHALL 按职责放入明确子包
- **AND** 持久化实现 SHALL NOT 与核心领域模型无边界混放

### Requirement: Production 运行时职责拆分
系统 SHALL 将 `production` 下的配置、健康检查、状态、预算、遥测、快照、工作区和基础设施实现拆分为清晰子包，避免 `production` 成为无边界公共工具包。

#### Scenario: 拆分生产运行时类
- **WHEN** `production` 包被整理
- **THEN** 配置类、health/readiness 类、state store 类、budget 类、telemetry 类、snapshot 类和 workspace 类 SHALL 分别进入对应职责边界
- **AND** 新包结构 SHALL 能从包名识别类的主要职责

#### Scenario: 处理跨域健康检查
- **WHEN** 生产健康检查需要判断 session、knowledge、tool、audit 或 telemetry store 状态
- **THEN** 健康检查 SHALL 通过明确的服务、接口或能力模型表达依赖
- **AND** `production` SHALL NOT 被扩展为直接承载其他业务域实现细节的公共包

### Requirement: 持久化实现边界
系统 SHALL 保留业务服务依赖的 store 接口或抽象边界，并 SHALL 将 `Jdbc*Store`、`Redis*Store`、`InMemory*Store` 等具体实现放入对应 persistence 或 infrastructure 边界。

#### Scenario: 移动 JDBC 实现
- **WHEN** JDBC store 类被移动
- **THEN** 应用服务 SHALL 继续依赖稳定 store 接口
- **AND** JDBC 实现 SHALL 位于清晰的 persistence 或 infrastructure 子包

#### Scenario: 移动测试中的实现引用
- **WHEN** 测试直接构造 InMemory、Jdbc 或 Redis store 实现
- **THEN** 测试 imports SHALL 与新的实现包路径同步
- **AND** 测试 SHALL NOT 通过回退到旧包路径来规避包结构整理

### Requirement: 行为等价约束
包结构整理 SHALL 保持运行时行为等价，并 SHALL NOT 改变 REST URL、请求字段、响应字段、SSE 事件、异常映射、数据库表名、配置 key、索引语义或业务数据模型。

#### Scenario: API 包移动后调用接口
- **WHEN** API controller 或 DTO 包路径发生变化
- **THEN** 现有 REST endpoint 路径 SHALL 保持不变
- **AND** 现有请求/响应 JSON 形态 SHALL 保持不变

#### Scenario: 数据库相关类移动
- **WHEN** store 接口或实现类包路径发生变化
- **THEN** 现有表名、列名、索引和查询语义 SHALL 保持不变
- **AND** 已有 migration SHALL NOT 因包结构整理被改写

### Requirement: 治理链路不可绕过
包结构整理 SHALL 保持聊天、RAG、工具、运行时和生产 readiness 的治理调用顺序，不得因移动类或提取方法绕过安全、权限、预算、审计、遥测或持久化约束。

#### Scenario: 聊天链路整理
- **WHEN** `ChatService` 周边类被移动或拆分
- **THEN** 请求 SHALL 仍通过 `RuntimeContextFactory` 建立租户、用户、Agent、会话上下文
- **AND** prompt safety、budget、消息持久化、RAG、Agent runtime 和 telemetry 的既有顺序 SHALL 保持不变

#### Scenario: 工具链路整理
- **WHEN** `ToolService` 周边类被移动或拆分
- **THEN** 工具执行 SHALL 仍保留启用状态、租户归属、权限策略、参数校验、prompt safety、高风险确认/审批、幂等、执行、审计和遥测链路
- **AND** 任何一步 SHALL NOT 被包结构整理删除或短路

### Requirement: RAG 权限和无答案策略保持不变
包结构整理 SHALL 保留 RAG 的租户隔离、用户/部门/角色过滤、引用来源和无可访问证据时的 no-answer 行为。

#### Scenario: 无权限证据不可返回
- **WHEN** 检索命中其他租户或当前用户无权访问的知识片段
- **THEN** 系统 SHALL 不返回该证据
- **AND** 在无可访问证据时 SHALL 返回 no-answer

#### Scenario: 有权限证据保留引用
- **WHEN** 检索到当前主体可访问的知识片段
- **THEN** 响应 SHALL 仍包含 citation 信息
- **AND** chunk、source 和 version 语义 SHALL 保持不变

### Requirement: 测试和文档同步
系统 SHALL 在包结构调整时同步更新对应测试包、imports、项目结构文档和开发规范说明。

#### Scenario: 移动生产代码包
- **WHEN** 生产代码类被移动到新包
- **THEN** 对应测试 SHALL 更新到新的包路径或 imports
- **AND** 相关单元测试 SHALL 能通过编译和执行

#### Scenario: 更新项目文档
- **WHEN** 包结构目标边界被确认
- **THEN** 项目文档 SHALL 描述新的主要包职责
- **AND** 文档 SHALL 标注 API、业务、持久化、生产运行时和控制台边界

### Requirement: 禁止无关框架漂移
包结构规范化 SHALL NOT 引入 Lombok、MapStruct、新日志框架、新代码生成工具、多 Maven 模块或微服务拆分作为达成目标的前置条件。

#### Scenario: 审查依赖变化
- **WHEN** 包结构整理提交被审查
- **THEN** 构建文件 SHALL NOT 出现与本变更目标无关的新框架依赖
- **AND** 代码 SHALL NOT 依赖新框架来替代现有构造器注入、DTO 或 store 实现模式
