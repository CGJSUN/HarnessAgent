## Why

项目需要一份可落地的企业级 Agent 需求设计，用 AgentScope Java v2 和 HarnessAgent 搭建工程化 Agent，而不是临时拼接 Agent 能力。`docs/start.md` 已经给出了建设方向，本变更将其收敛为 OpenSpec 能力、需求、设计和任务清单，便于后续实施与验收。

## What Changes

- 定义企业级 Agent 平台的分阶段范围：先完成 MVP 聊天闭环，再扩展 RAG、受治理的工具调用、生产运行时、安全治理、管理/运营控制台和多 Agent 平台化。
- 定义 Spring Boot + HarnessAgent 的核心运行契约，包括聊天 API、会话隔离、流式响应、工作区处理和模型提供方抽象。
- 定义企业知识库与 RAG 要求，包括知识接入、文档处理、混合检索、引用来源、版本管理和权限过滤。
- 定义受治理的企业工具调用要求，包括只读工具、高风险工具、人工确认、MCP 接入和工具审计。
- 定义多 Agent 编排要求，包括 Supervisor 路由、专家 Agent、Agent-as-Tool、任务编排和 Skill 仓库治理。
- 将生产运行时、安全治理、管理/运营控制台拆成独立能力，避免把所有企业级治理要求混在一个不可验收的大能力中。

当前仓库没有既有 OpenSpec 能力或应用代码，因此本变更不引入破坏性变更。

## Capabilities

### New Capabilities

- `enterprise-agent-core`: 企业 Agent 核心运行时 API、HarnessAgent 构建、模型抽象、会话隔离、流式响应、工作区和状态行为。
- `knowledge-rag`: 知识源接入、文档处理、混合检索、引用来源、版本管理和带数据权限的检索。
- `governed-tool-calling`: 企业系统工具、工具风险分级、Human-in-the-loop 确认、MCP 接入和工具审计。
- `multi-agent-orchestration`: Supervisor 模式、专家 Agent、Agent-as-Tool 组合、Planning、Routing、Handoffs 和 Skill 仓库管理。
- `production-runtime`: 多租户生产状态、DistributedStore、远程工作区、沙箱与快照策略、可观测性、限流和模型 fallback。
- `security-governance`: 身份接入、RBAC、数据权限、工具权限、Agent 权限、Prompt/工具安全、数据保护和审计留存。
- `admin-ops-console`: 用户端、管理端和运营端工作流，包括聊天、文件上传、Agent 配置、Prompt/工具/知识库/Skill 管理、指标、反馈和成本分析。

### Modified Capabilities

- 无。

## Impact

- 影响架构：Spring Boot 服务边界、HarnessAgent 配置、AgentScope Java v2 集成、状态/工作区存储、模型提供方抽象、RAG 提供方、企业工具适配器和多 Agent 编排。
- 影响 API：聊天、会话、消息、文件/知识接入、工具执行确认、管理后台和运营报表接口。
- 影响基础设施：Redis 或 MySQL DistributedStore、RemoteFilesystem 或 Sandbox 工作区、OSS/S3/MinIO/JDBC 快照存储、OpenTelemetry、企业身份源和可选 MCP Server。
- 影响安全模型：tenant/user/session/agent key 设计、RBAC、数据权限、工具权限、Prompt/工具安全、密钥管理、日志脱敏和审计留存。
