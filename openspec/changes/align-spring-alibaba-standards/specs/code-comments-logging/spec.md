## ADDED Requirements

### Requirement: 必要注释范围
系统 SHALL 只为非显而易见的策略、安全边界、治理顺序、状态保存时机、幂等/审计/降级取舍和复杂 Agent/RAG/工具交互补充注释，并 SHALL NOT 为简单赋值、构造器、getter 或直观条件分支添加机械注释。

#### Scenario: 补充治理注释
- **WHEN** 代码包含 prompt safety、RAG 无答案、工具审批、幂等、审计、状态保存、fallback 或 readiness fail-fast 等关键策略
- **THEN** 注释 SHALL 解释策略目的和边界
- **AND** 注释 SHALL NOT 复述代码逐行做了什么

#### Scenario: 审查机械注释
- **WHEN** 新增注释只描述变量赋值、返回值或方法名称已经表达的行为
- **THEN** 该注释 SHALL 被移除或改写为说明真实业务约束

### Requirement: 治理顺序注释
系统 SHALL 在聊天、RAG、工具执行、生产运行时和多 Agent 编排的关键链路上标注不可随意调整的治理顺序。

#### Scenario: 聊天链路注释
- **WHEN** 聊天服务执行 prompt safety、budget、消息持久化、RAG 和模型调用
- **THEN** 代码 SHALL 注释说明该顺序服务于安全、成本和上下文隔离
- **AND** 注释 SHALL 明确无可访问证据时不能让模型自由编造答案

#### Scenario: 工具链路注释
- **WHEN** 工具执行经过 preflight、权限、参数白名单、Prompt 注入检查、高风险审批、幂等和审计
- **THEN** 代码 SHALL 注释说明这些步骤的顺序约束
- **AND** 后续维护者 SHALL 能从注释识别哪些步骤不得被短路

### Requirement: 使用 SLF4J 参数化日志
系统 SHALL 使用 Spring Boot 默认 SLF4J 进行应用日志记录，并 MUST 使用 `{}` 参数化占位表达结构化上下文。

#### Scenario: 新增日志语句
- **WHEN** 代码新增日志
- **THEN** 日志 SHALL 使用 SLF4J logger
- **AND** 日志 SHALL 使用参数占位符传入上下文字段
- **AND** 日志 SHALL NOT 使用字符串拼接或 `String.format` 构造日志消息

#### Scenario: 禁止输出到标准流
- **WHEN** 代码需要记录运行时事件或异常
- **THEN** 代码 SHALL NOT 使用 `System.out`、`System.err`、`printStackTrace` 或 `java.util.logging`
- **AND** 代码 SHALL NOT 通过字符串拼接 dump DTO、Map 或异常消息

### Requirement: 关键治理事件日志
系统 SHALL 在关键拒绝、降级、治理操作、执行异常、生产 readiness 阻断、状态/快照失败和编排切换路径记录必要日志。

#### Scenario: 聊天和 RAG 事件
- **WHEN** 发生预算拒绝、Prompt 安全拒绝、RAG 无可访问证据、模型调用失败或流式输出异常
- **THEN** 系统 SHALL 记录包含组件、状态、原因码、耗时或数量的日志
- **AND** 日志 SHALL NOT 包含原始用户消息、query、构造后的 RAG prompt、模型回复或 SSE delta

#### Scenario: 工具治理事件
- **WHEN** 发生未知工具、租户不匹配、权限拒绝、参数校验失败、高风险待确认、幂等冲突、重复命中、执行器异常或超时
- **THEN** 系统 SHALL 记录可定位工具治理状态的日志
- **AND** 日志 SHALL NOT 包含原始工具参数、工具结果、参数 fingerprint 或幂等 key 原文

#### Scenario: 生产和编排事件
- **WHEN** 发生 production readiness 失败、durable store 不可用、状态保存失败、快照保存/恢复失败、workspace 策略拒绝、model fallback、路由升级人工或 handoff 失败
- **THEN** 系统 SHALL 记录受控上下文日志
- **AND** 日志 SHALL NOT 包含 Agent state、snapshot 内容、workspace 文件内容、完整本地路径或外部存储 URI

### Requirement: 日志级别约束
系统 SHALL 按事件严重度使用日志级别，避免将高频成功路径提升为 INFO，也避免将治理拒绝吞没为 DEBUG。

#### Scenario: 成功和低频生命周期事件
- **WHEN** 记录工具注册、知识源变更、路由成功、chat/tool/orchestration 完成摘要或快照创建成功
- **THEN** 系统 SHALL 使用 INFO 或更低噪声的级别
- **AND** 高频成功路径细节 SHALL 使用 DEBUG

#### Scenario: 拒绝和失败事件
- **WHEN** 记录治理拒绝、预算超限、Prompt 注入拦截、高风险拒绝、幂等冲突、fallback、timeout 或 RAG 权限过滤导致无答案
- **THEN** 系统 SHALL 使用 WARN
- **AND** 生产启动不可用、durable persistence 不可用、状态/快照失败导致请求失败或未处理基础设施异常 SHALL 使用 ERROR

### Requirement: 日志字段 allowlist 和脱敏
系统 SHALL 采用日志字段 allowlist，只允许记录组件名、状态、原因码、耗时、数量、布尔结果以及受控标识，并 MUST 对可能含 PII 或外部输入的标识进行哈希或摘要处理。

#### Scenario: 记录用户和会话定位信息
- **WHEN** 日志需要关联用户、会话或幂等请求
- **THEN** `userId`、`sessionId` 和 `idempotencyKey` SHALL 以哈希或后缀摘要形式记录
- **AND** 日志 SHALL NOT 记录邮箱、手机号、工号、完整角色列表或完整部门列表

#### Scenario: 记录安全上下文
- **WHEN** 日志记录租户、Agent、工具或知识源上下文
- **THEN** 如需记录定位信息，日志 SHALL 只记录受控的 `tenantId`、`agentId`、`toolId`、`sourceId`
- **AND** 日志 SHALL NOT 记录 source title、反馈评论或可能暴露业务语义的文本内容

### Requirement: 敏感内容禁止入日志
系统 SHALL 禁止原始 prompt、query、文档内容、chunk 内容、工具输入输出、凭据、连接串、状态值、快照内容和工作区内容进入应用日志。

#### Scenario: 记录异常
- **WHEN** 捕获异常并写入日志
- **THEN** 日志 SHALL 优先记录异常类型、脱敏原因码和受控上下文字段
- **AND** 只有基础设施异常需要排障时 SHALL 记录 throwable 堆栈
- **AND** 调用点 SHALL 确保异常消息不携带 prompt、工具参数、state、snapshot 或 URI

#### Scenario: 记录 Map 或 attributes
- **WHEN** 日志需要记录 Map、工具参数、工具结果或 telemetry attributes 中的派生信息
- **THEN** 代码 SHALL 先通过 allowlist 或脱敏函数提取安全字段
- **AND** 代码 SHALL NOT 直接记录原始 Map、DTO 或 attributes 对象

### Requirement: 日志不替代审计和遥测
应用日志 SHALL 辅助生产排障，但 SHALL NOT 替代安全审计、工具审计、RAG 指标、运行时 telemetry 或持久化业务记录。

#### Scenario: 工具执行完成
- **WHEN** 工具调用完成、拒绝、待审批、失败或命中幂等记录
- **THEN** 系统 SHALL 保留既有工具审计和 telemetry 行为
- **AND** 新增日志 SHALL NOT 成为唯一的审计来源

#### Scenario: RAG 检索完成
- **WHEN** RAG 检索命中、无答案或被权限过滤为空
- **THEN** 系统 SHALL 保留既有 RAG metric 或 feedback 记录语义
- **AND** 新增日志 SHALL NOT 记录原始 query 或证据内容
