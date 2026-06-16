## Why

当前项目目标从“企业级受治理 Agent 平台”调整为“个人版完整 Agent”。新方向需要围绕单用户本地/个人工作流，完整覆盖 AgentScope Java v2 中文文档中的核心组件、Harness 工程能力、多 Agent、工具、权限、记忆、RAG、状态、技能、集成和生产参考，而不是继续优先建设多租户企业治理平台。

## What Changes

- **BREAKING**：产品定位从企业级多租户 Agent 平台调整为个人版完整 Agent 应用，默认运行时以个人身份、个人工作区、个人记忆和本地优先配置为中心。
- **BREAKING**：企业级租户隔离、企业 RBAC、企业数据权限、企业审计报表、管理/运维控制台和发布门禁不再作为核心验收目标；只保留个人使用所需的最小安全、确认、日志和可观测能力。
- 引入个人 Agent 核心运行闭环，覆盖非流式/流式对话、消息与事件、模型适配、上下文、AgentState、会话恢复和预算/超时控制。
- 完整对齐 AgentScope Java v2 的 Harness 能力，包括工作区、文件系统、沙箱、子 Agent、技能、计划模式、通道、上下文压缩和长期任务协作。
- 支持个人知识与记忆能力，包括本地文档/文件接入、轻量 RAG、引用来源、个人记忆读写和可配置的知识库/记忆后端。
- 支持个人工具系统，包括工具注册、参数校验、权限确认、Human-in-the-loop、幂等、工具结果结构化展示和最小审计日志。
- 支持多 Agent 和 Agent-as-Tool 个人编排，包括 supervisor、routing、handoff、planner、specialist agent、subagent 和可复用技能。
- 支持 AgentScope Java v2 文档中的集成面，包括模型、memory provider、Agent state store、RAG knowledge base、skill repository、agent protocol 和基础设施集成的本地优先适配。
- 控制台从企业运营后台转为个人 Agent 工作台，聚焦聊天、任务、工作区文件、知识/记忆、工具、技能、运行轨迹和配置管理。

## Capabilities

### New Capabilities

- `personal-agent-core`: 个人 Agent 的运行时、聊天 API、流式事件、消息模型、模型提供方、上下文、AgentState、会话恢复、预算、超时和 fallback 行为。
- `agentscope-v2-complete-coverage`: 对齐 AgentScope Java v2 中文文档的核心组件、Harness、参考指南和迁移/生产建议，定义项目如何声明“完整覆盖”。
- `personal-workspace-runtime`: 个人工作区、文件系统、沙箱、快照、上下文压缩、计划模式、通道和长期任务状态管理。
- `personal-memory-rag`: 个人记忆、知识源接入、文档处理、检索、引用来源、无答案策略和可插拔 memory/RAG 后端。
- `personal-tooling-hitl`: 个人工具注册、参数校验、权限确认、Human-in-the-loop、幂等、工具执行轨迹和本地审计。
- `personal-multi-agent-orchestration`: supervisor、routing、handoff、planner、specialist agent、Agent-as-Tool、subagent 和多 Agent trace。
- `personal-skill-integration`: 技能仓库、技能加载、技能执行、技能权限、技能版本管理以及与 AgentScope/Harness skill 能力的适配。
- `personal-agent-workbench`: 个人版 Web 工作台，覆盖聊天、任务、文件、知识/记忆、工具、技能、运行轨迹、配置和本地诊断。

### Modified Capabilities

- 无。当前仓库没有已归档的 `openspec/specs/` 主规格；现有企业版规格仍位于未归档变更下，本变更以新的个人版能力集合重新定义目标范围。

## Impact

- 影响产品范围：从企业平台 MVP 转为个人 Agent 应用，企业多租户治理和运营后台能力降级为非目标或后续可选扩展。
- 影响后端：Spring Boot API、AgentScope Java v2/HarnessAgent 集成、会话与状态、工作区、沙箱、工具执行、记忆/RAG、多 Agent 编排和配置模型需要按个人版重新设计。
- 影响前端：`web/` 控制台需要从企业管理/运维视图调整为个人工作台，优先支持任务执行、工作区文件、知识/记忆、工具、技能、trace 和配置。
- 影响文档：需要更新 `docs/`、`web/README.md` 和后续 OpenSpec specs/design/tasks，统一使用中文描述个人版目标、非目标、验收边界和 AgentScope Java v2 覆盖范围。
- 影响依赖和集成：需要评估 AgentScope Java v2、模型 provider、memory provider、state store、RAG knowledge base、skill repository、agent protocol、沙箱和本地文件系统适配。
- 影响测试：需要新增覆盖个人聊天、流式事件、工具确认、多 Agent 编排、技能执行、记忆/RAG、工作区、状态恢复和个人工作台流程的后端、前端和端到端测试。
