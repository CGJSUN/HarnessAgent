## ADDED Requirements

### Requirement: 工作台外壳
系统 SHALL 提供基于浏览器的企业 Agent 工作台作为主 Web UI，并提供面向用户聊天、管理、运营、审计、发布就绪和编排视图的角色感知导航。

#### Scenario: 用户打开 Web UI
- **WHEN** 授权用户打开 Web UI
- **THEN** 系统 SHALL 展示包含导航和当前 Agent 工作区的运营型工作台，而不是营销页或文档落地页

#### Scenario: 角色受限导航
- **WHEN** 用户缺少访问管理、运营或审计视图所需的角色
- **THEN** UI SHALL 隐藏或禁用受限导航，并且底层 API 调用仍 SHALL 依赖后端授权

### Requirement: 聊天控制台
系统 SHALL 提供用户聊天控制台，支持会话导航、消息历史、消息输入、RAG 启用、流式响应、引用、无答案状态和可见运行状态。

#### Scenario: 用户发送流式聊天消息
- **WHEN** 用户从聊天控制台发送消息
- **THEN** UI SHALL 调用后端流式聊天 API，并将收到的文本事件追加到当前 assistant 消息，直到收到 terminal 事件或错误

#### Scenario: 用户加载既有会话
- **WHEN** 用户选择一个历史会话
- **THEN** UI SHALL 从后端加载该会话的消息历史，并按时间顺序展示消息

#### Scenario: RAG 回答包含引用
- **WHEN** 聊天响应包含引用来源
- **THEN** UI SHALL 在 assistant 回答附近展示引用来源元数据

#### Scenario: RAG 证据不足
- **WHEN** 后端返回无答案原因
- **THEN** UI SHALL 展示无答案状态，并且不得把该回答呈现为有依据的知识回答

### Requirement: 工具确认体验
系统 SHALL 展示高风险工具确认，包含操作摘要、脱敏参数、会话上下文，以及明确的确认或拒绝动作。

#### Scenario: 确认待处理
- **WHEN** 后端报告当前用户或会话存在待确认工具操作
- **THEN** UI SHALL 在执行继续前展示工具名称、脱敏输入、受影响会话和可用用户动作

#### Scenario: 用户确认高风险工具
- **WHEN** 用户确认一个待处理高风险工具操作
- **THEN** UI SHALL 将确认上下文提交给后端，并刷新工具状态和可审计状态

#### Scenario: 用户拒绝高风险工具
- **WHEN** 用户拒绝一个待处理高风险工具操作
- **THEN** UI SHALL 停止把该操作呈现为已批准，并展示后端返回的状态或错误

### Requirement: 管理视图
系统 SHALL 提供管理员视图，覆盖后端支持的 Agent 配置、工具管理、知识源管理和 Skill 生命周期操作。

#### Scenario: 管理员更新 Agent 配置
- **WHEN** 管理员编辑 Agent prompt 或运行配置并提交变更
- **THEN** UI SHALL 调用对应后端 console API，并展示更新后的 Agent 元数据或后端校验错误

#### Scenario: 管理员启用或禁用工具
- **WHEN** 管理员修改工具启用状态
- **THEN** UI SHALL 提交状态变更，并刷新工具列表以展示当前启用状态

#### Scenario: 管理员查看知识源
- **WHEN** 管理员打开知识管理
- **THEN** UI SHALL 展示每个知识源的标题、版本、可见范围、状态和后端暴露的可用管理动作

#### Scenario: 管理员管理 Skill 生命周期
- **WHEN** 管理员打开 Skill 管理
- **THEN** UI SHALL 展示 Skill 版本、审批状态、发布状态、禁用状态和已支持的生命周期动作

### Requirement: 运营和审计视图
系统 SHALL 提供运营和审计视图，覆盖指标、成本、发布就绪、编排 trace 和可搜索审计记录。

#### Scenario: 运维查看平台指标
- **WHEN** 运维人员打开指标视图
- **THEN** UI SHALL 展示后端返回的会话、模型、工具、RAG、失败、耗时和反馈指标

#### Scenario: 运维查看成本报表
- **WHEN** 运维人员按 Agent 或模型提供方过滤成本
- **THEN** UI SHALL 请求过滤后的后端成本报表，并按可用维度展示 Token 用量和估算成本

#### Scenario: 审计员搜索审计记录
- **WHEN** 审计员按用户、会话、资源、动作或时间范围提交审计过滤条件
- **THEN** UI SHALL 在用户后端授权范围内展示匹配的审计记录

#### Scenario: 用户查看发布就绪
- **WHEN** 授权用户打开发布就绪视图
- **THEN** UI SHALL 展示后端返回的 MVP 场景、阶段门禁、回滚动作和验收清单

#### Scenario: 用户查看编排 trace
- **WHEN** 授权用户打开编排 trace 视图
- **THEN** UI SHALL 展示后端返回的 Supervisor 决策、handoff、工具调用、失败和最终回答组装元数据

### Requirement: 身份和授权处理
系统 SHALL 支持本地开发身份模拟，同时保留生产环境中身份和角色由可信网关或认证层提供的假设。

#### Scenario: 本地开发者设置身份
- **WHEN** 本地开发者在 UI 中设置 tenant、user、role 或 department
- **THEN** UI SHALL 使用后端支持的本地开发身份契约把这些值包含在 API 请求中

#### Scenario: 后端拒绝身份不匹配
- **WHEN** 后端因为可信身份和请求身份不匹配而拒绝请求
- **THEN** UI SHALL 展示后端错误消息，并且 SHALL NOT 静默使用修改后的身份值重试

#### Scenario: 后端拒绝特权访问
- **WHEN** 后端因为授权不足拒绝管理、运营或审计请求
- **THEN** UI SHALL 展示访问被拒绝状态，即使 HTTP 状态码不是标准 `401` 或 `403`

### Requirement: API Client 和错误处理
系统 SHALL 提供前端 API client，一致处理后端请求结构、响应结构、流式事件、取消、加载状态和错误响应。

#### Scenario: 后端返回结构化错误
- **WHEN** API 调用返回后端错误响应
- **THEN** UI SHALL 展示错误消息，并保留足够上下文供用户恢复或重试

#### Scenario: 流式请求被取消
- **WHEN** 用户取消进行中的流式聊天请求
- **THEN** UI SHALL 停止读取 stream，将 assistant 响应标记为已取消，并允许用户发送下一条消息

#### Scenario: 流式请求失败
- **WHEN** 流式请求在 terminal 事件前失败
- **THEN** UI SHALL 保留部分响应、展示失败状态，并提供重试路径

### Requirement: 本地和生产托管
系统 SHALL 支持本地前端开发连接 Spring Boot 后端，并支持避免不必要跨源浏览器调用的生产托管路径。

#### Scenario: 开发者本地运行前端
- **WHEN** 开发者启动前端开发服务器
- **THEN** 对 `/api/**` 的请求 SHALL 被代理到 Spring Boot 后端，并且不要求直接配置浏览器 CORS

#### Scenario: 生产环境托管 Web UI
- **WHEN** Web UI 部署到生产环境
- **THEN** 它 SHALL 由 Spring Boot 应用托管，或由能为后端 API 调用提供可信身份上下文的同源网关路径托管

### Requirement: 浏览器验证
系统 SHALL 包含主 Web UI 工作流和响应式布局的浏览器级验证。

#### Scenario: 聊天工作流被验证
- **WHEN** Web UI 实现被测试
- **THEN** 自动化浏览器验证 SHALL 覆盖加载工作台、选择或创建会话、发送聊天消息、接收回答，以及展示引用或无答案状态

#### Scenario: 管理工作流被验证
- **WHEN** Web UI 实现被测试
- **THEN** 自动化浏览器验证 SHALL 覆盖至少一个管理视图、一个运营或审计视图、错误展示和响应式布局行为
