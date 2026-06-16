# AgentScope Java v2 官方文档覆盖矩阵

本矩阵用于定义 HarnessAgent 何时可以声明“覆盖 AgentScope Java v2 官方文档能力”。基线为 AgentScope Java v2 中文文档，索引页最后更新时间按 OpenSpec 记录为 2026-06-09；本仓库当前依赖 `io.agentscope:agentscope-harness:2.0.0-RC3`。

官方入口：https://java.agentscope.io/v2/zh/docs/index.html

状态说明：

- 已实现：当前代码已有可运行实现，并有对应测试。
- 部分覆盖：当前代码有基础实现，但个人版语义、覆盖面或测试仍有缺口。
- 待实现：仅有 OpenSpec 任务或设计目标，代码尚未覆盖。
- 文档/接口：当前只作为文档化边界、接口预留或后续 provider 扩展。

## 快速开始与核心组件

| 官方页面 | 官方 URL | 版本 / 更新时间 | capability 映射 | 实现状态 | 测试状态 |
|---|---|---|---|---|---|
| Quickstart | https://java.agentscope.io/v2/zh/docs/quickstart.html | v2 / 2026-06-09 | `personal-agent-core` | 部分覆盖：已有 Spring Boot 聊天入口、echo/DashScope/OpenAI-compatible provider 和 AgentScope Harness 依赖；个人默认上下文已收敛到 `tenantId=personal`、`userId=owner`、`runtimeUserId=personal:<owner>` | 已补个人默认上下文、API identity、ChatService 和 provider 配置测试 |
| Agent | https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html | v2 / 2026-06-09 | `personal-agent-core`、`personal-multi-agent-orchestration` | 部分覆盖：已有 ReActAgent 适配、个人 Agent 配置和 AgentState 恢复；子 Agent 规格待实现 | runtime、provider、状态恢复和个人上下文测试已补；子 Agent 隔离测试待补 |
| Message / Event | https://java.agentscope.io/v2/zh/docs/building-blocks/message-and-event.html | v2 / 2026-06-09 | `personal-agent-core`、`personal-agent-workbench` | 部分覆盖：已有 ContentBlock 消息持久化、非流式结构化响应和类型化流式事件；前端完整附件/媒体/HITL 展示待完善 | 已补 API、ChatService、ContentBlock、JDBC 存储和流式事件映射测试 |
| Middleware | https://java.agentscope.io/v2/zh/docs/building-blocks/middleware.html | v2 / 2026-06-09 | `personal-agent-core`、`personal-tooling-hitl` | 待实现：需评估 Java v2 中间件钩子与预算、超时、权限、trace 的接入点 | 待补中间件链路测试 |
| Model | https://java.agentscope.io/v2/zh/docs/building-blocks/model.html | v2 / 2026-06-09 | `personal-agent-core` | 部分覆盖：已有统一 ModelProvider 配置解析，支持个人 Agent 级 provider、model、密钥引用、预算、Agent 级 fallback，以及 echo、DashScope、OpenAI-compatible provider；外部凭据和真实供应商验收仍按环境配置执行 | 已补 resolver、provider registry、DashScope/OpenAI-compatible、ChatService 预算和 runtime fallback 测试 |
| Permission System | https://java.agentscope.io/v2/zh/docs/building-blocks/permission-system.html | v2 / 2026-06-09 | `personal-tooling-hitl`、`personal-skill-integration` | 部分覆盖：企业 RBAC 和工具权限已有；个人授权三态、可撤销授权和技能权限待实现 | 企业权限测试已有；个人 HITL 和技能权限测试待补 |
| Tool | https://java.agentscope.io/v2/zh/docs/building-blocks/tool.html | v2 / 2026-06-09 | `personal-tooling-hitl` | 部分覆盖：已有工具注册、schema、风险、确认、幂等、审计；Shell/SQL/code/untrusted 工具确认后会接入沙箱策略；结构化结果和 MCP/protocol 来源待完善 | 工具治理、沙箱确认和沙箱 executor 接入测试已补；结构化结果、MCP 来源和恢复测试待补 |
| Context / AgentState | https://java.agentscope.io/v2/zh/docs/building-blocks/context.html | v2 / 2026-06-09 | `personal-agent-core`、`personal-workspace-runtime` | 部分覆盖：已有 RuntimeContextFactory、Local JSON/Redis/JDBC AgentState store、个人 owner 默认映射、恢复快照和 pending execution marker；workspace 快照恢复仍属后续任务 | Agent state store、个人默认映射、恢复快照、pending 状态和重复历史规避测试已补 |

## Harness 能力

| 官方页面 | 官方 URL | 版本 / 更新时间 | capability 映射 | 实现状态 | 测试状态 |
|---|---|---|---|---|---|
| Harness architecture | https://java.agentscope.io/v2/zh/docs/harness/architecture.html | v2 / 2026-06-09 | `personal-workspace-runtime`、`personal-agent-workbench` | 部分覆盖：已有生产运行时、健康检查和 Spring Boot 服务；个人工作台入口待重构 | 生产运行时测试已有；个人端到端验收待补 |
| Workspace | https://java.agentscope.io/v2/zh/docs/harness/workspace.html | v2 / 2026-06-09 | `personal-workspace-runtime` | 部分覆盖：已有 workspace/snapshot 配置和持久化；个人 owner/agent 目录结构、`workspace.json` 元数据、文件保存、下载、删除和引用 metadata 已实现；前端文件视图待实现 | snapshot、个人 workspace 初始化和文件服务测试已补；个人文件视图测试待补 |
| Filesystem | https://java.agentscope.io/v2/zh/docs/harness/filesystem.html | v2 / 2026-06-09 | `personal-workspace-runtime` | 部分覆盖：已有 owner/agent 工作区路径边界，支持相对路径和 `workspace://` URI 解析，并拒绝绝对路径与路径穿越；服务层上传、生成文件保存、引用定位、下载和删除已实现 | 已补路径安全和文件操作测试；前端文件视图测试待补 |
| Sandbox | https://java.agentscope.io/v2/zh/docs/harness/sandbox.html | v2 / 2026-06-09 | `personal-workspace-runtime`、`personal-tooling-hitl` | 部分覆盖：已有生产 sandbox 配置和 snapshot 要求；已新增沙箱执行策略抽象、本地子进程/Docker/远端执行器适配点；Shell/SQL/code/untrusted 工具执行前要求确认并接入沙箱策略；真实 Docker/远端 runner 仍是适配点 | 健康检查边界、沙箱策略选择、路径安全和工具沙箱接入测试已补；真实 runner 测试待后续接线 |
| Memory | https://java.agentscope.io/v2/zh/docs/harness/memory.html | v2 / 2026-06-09 | `personal-memory-rag` | 部分覆盖：AgentState 可持久化；分层个人记忆、记忆工具、删除和导出待实现 | state store 测试已有；个人记忆测试待补 |
| Subagent | https://java.agentscope.io/v2/zh/docs/harness/subagent.html | v2 / 2026-06-09 | `personal-multi-agent-orchestration` | 部分覆盖：已有 supervisor/route/handoff/trace 企业骨架；个人子 Agent 规格和后台委派待实现 | 编排基础测试已有；subagent 边界测试待补 |
| Skill | https://java.agentscope.io/v2/zh/docs/harness/skill.html | v2 / 2026-06-09 | `personal-skill-integration` | 部分覆盖：已有 Skill 治理服务；本地技能仓库、加载、版本锁定和验证待实现 | 企业 Skill 生命周期测试已有；个人技能测试待补 |
| Plan Mode | https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html | v2 / 2026-06-09 | `personal-workspace-runtime` | 待实现：计划模式需要只读生成计划、落盘到 `workspace/plans/` 并与执行模式衔接 | 待补计划模式测试 |
| Channel | https://java.agentscope.io/v2/zh/docs/harness/channel.html | v2 / 2026-06-09 | `personal-agent-core`、`personal-agent-workbench` | 待实现：需要用户可见、工具事件、计划更新、系统提醒和诊断 channel 分层 | 待补前后端 channel 渲染测试 |
| Compaction | https://java.agentscope.io/v2/zh/docs/harness/compaction.html | v2 / 2026-06-09 | `personal-workspace-runtime` | 待实现：需要结构化上下文压缩，保留目标、状态、决策、文件引用和下一步 | 待补压缩和恢复测试 |

## 参考与集成

| 官方页面 | 官方 URL | 版本 / 更新时间 | capability 映射 | 实现状态 | 测试状态 |
|---|---|---|---|---|---|
| Change log / Migration | https://java.agentscope.io/v2/zh/docs/change-log.html | v2 / 2026-06-09 | `agentscope-v2-complete-coverage` | 文档/接口：用于校验 Java v2 API 面和本仓库适配层差异 | 待由 9.1 脚本校验 |
| Going to production | https://java.agentscope.io/v2/zh/docs/others/going-to-production.html | v2 / 2026-06-09 | `agentscope-v2-complete-coverage`、`personal-agent-core` | 部分覆盖：已有 production profile、state、telemetry、budget 和 health check；个人版 gate 待完善 | 生产运行时测试已有；个人发布验收待补 |
| FAQ / Release notes | https://java.agentscope.io/v2/zh/docs/others/faq.html | v2 / 2026-06-09 | `agentscope-v2-complete-coverage` | 文档/接口：作为覆盖矩阵复核入口 | 待由 9.1 脚本校验 |
| Memory integration | https://java.agentscope.io/v2/zh/integration/memory/overview.html | v2 / 2026-06-09 | `personal-memory-rag` | 文档/接口：本地优先，预留 Bailian、Mem0、ReMe 等 provider | 待补 provider 切换测试 |
| RAG integration | https://java.agentscope.io/v2/zh/integration/rag/overview.html | v2 / 2026-06-09 | `personal-memory-rag` | 部分覆盖：已有轻量 lexical RAG、引用、无答案；个人知识源、索引版本和外部 provider 待完善 | RAG 企业测试已有；个人知识/RAG 测试待补 |
| Session / State integration | https://java.agentscope.io/v2/zh/integration/session/overview.html | v2 / 2026-06-09 | `personal-agent-core`、`personal-workspace-runtime` | 部分覆盖：JDBC/Redis/本地 JSON AgentState store、会话消息持久化和 pending execution 恢复已覆盖；长任务 workspace/sandbox 快照恢复待后续任务完善 | store、JDBC session、恢复快照、pending 状态和 ChatService 恢复路径测试已补 |
| Skill repository integration | https://java.agentscope.io/v2/zh/integration/skill/overview.html | v2 / 2026-06-09 | `personal-skill-integration` | 待实现：需要本地技能仓库扫描，并预留 Git、MySQL、PostgreSQL 仓库适配 | 待补技能仓库测试 |
| Protocol integration | https://java.agentscope.io/v2/zh/integration/protocol/overview.html | v2 / 2026-06-09 | `personal-tooling-hitl`、`personal-skill-integration` | 文档/接口：MCP 当前主要是受治理工具来源；真实 client、超时和结果映射待实现 | MCP 来源治理测试已有；真实协议适配测试待补 |
| Ecosystem integration | https://java.agentscope.io/v2/zh/integration/ecosystem/overview.html | v2 / 2026-06-09 | `agentscope-v2-complete-coverage` | 文档/接口：Chat Completions Web、Studio、Training 等作为后续扩展 | 待补集成契约测试 |
| Infrastructure integration | https://java.agentscope.io/v2/zh/integration/infrastructure/overview.html | v2 / 2026-06-09 | `personal-workspace-runtime`、`personal-agent-core` | 部分覆盖：MySQL/JDBC、Redis state/budget、JDBC snapshot、telemetry 已有；对象存储和远端沙箱待接线 | durable persistence 测试已有；对象存储/远端沙箱测试待补 |

## 覆盖声明规则

- “已覆盖 AgentScope Java v2”只能用于单行能力状态，不得替代整体验收结论。
- “完整覆盖”要求所有必选页面至少达到已实现或明确文档化非目标，并有对应测试或校验脚本记录。
- 官方文档更新时间晚于 2026-06-09 时，矩阵状态自动视为需要复核。
- 外部 provider 没有真实凭据或基础设施接线时，只能标记为文档/接口或部分覆盖。
