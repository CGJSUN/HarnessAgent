## Context

当前仓库已经实现了企业级 Agent 平台 MVP 的大量后端与前端骨架，包括聊天、RAG、工具治理、生产运行时、控制台和多 Agent 相关包。但本变更的产品目标发生根本转向：从多租户企业平台调整为单用户个人版完整 Agent。

AgentScope Java v2 官方中文文档当前覆盖快速开始、核心组件、Harness、参考和集成。索引页最后更新时间为 2026-06-09，主要能力包括 Agent、Message/Event、Middleware、Model、Permission System、Tool、Context/AgentState，Harness 的 workspace、filesystem、sandbox、memory、subagent、skill、plan mode、channel、compaction，以及 memory、RAG、session、skill、protocol、ecosystem、infrastructure 等集成。

目标形态如下：

```text
个人用户
  Web 工作台 / 本地 API / 可选协议入口
        |
        v
Spring Boot 个人 Agent API
  chat, stream, sessions, workspace, tools, skills, memory, rag, traces
        |
        v
个人 Agent 应用服务
  Agent Core | Workspace | Memory/RAG | Tool HITL | Multi-Agent | Skill
        |
        v
AgentScope Java v2 / Harness
  Agent | Message/Event | Middleware | Model | Permission | Tool | Context/State
  Workspace | Filesystem | Sandbox | Memory | Subagent | Skill | Plan Mode | Channel
        |
        v
本地优先基础设施
  H2/SQLite/MySQL | JSON/Redis state | local/object workspace | local/git skill repo
```

## Goals / Non-Goals

**Goals:**

- 将项目重新定义为个人版完整 Agent，而不是企业治理平台。
- 对齐 AgentScope Java v2 中文文档的完整功能面，并形成可验收的覆盖矩阵。
- 保留现有 Spring Boot、React/Vite 和 AgentScope Java 集成基础，按个人版语义重构产品能力。
- 支持个人聊天、流式事件、多模态消息、模型配置、会话恢复、预算、超时和 fallback。
- 支持个人工作区、文件系统、沙箱、快照、上下文压缩、计划模式、通道和长期任务状态。
- 支持个人记忆、知识/RAG、工具 HITL、多 Agent、技能和个人工作台。

**Non-Goals:**

- 不再把企业多租户隔离、企业 RBAC、企业部门/角色数据权限、企业审计报表和运营后台作为核心验收目标。
- 不要求首版实现所有第三方集成的真实生产接线，但必须提供清晰的适配接口、配置入口、mock/本地实现和验收边界。
- 不默认允许不受控的 Shell、代码、数据库写入或外部系统写操作；个人版仍需要确认、沙箱和最小审计。
- 不把 AgentScope Java v1 文档作为本变更验收基线。

## Decisions

### Decision 1: 以个人 profile 替代企业 tenant profile

个人版 SHALL 使用 `personal` profile 作为默认运行模式。后端仍保留内部 context key 维度，但默认 `tenantId` 或 `orgId` SHALL 映射为个人空间标识，例如 `personal:<ownerId>`，避免现有状态、审计、RAG 和工具代码大范围失去隔离 key。

替代方案：

- 删除所有 tenant 维度：代码更简单，但会破坏现有状态 key、测试和未来多人扩展路径。
- 保持企业租户语义：迁移成本低，但产品和 UI 仍会被企业治理概念污染。

### Decision 2: 用覆盖矩阵定义“AgentScope Java v2 全部功能”

“全部功能” SHALL 被定义为官方 v2 中文文档页面级覆盖，而不是要求一次性接入所有外部供应商。每个官方页面需要落到以下状态之一：已实现、接口/本地实现已覆盖、文档化非目标、或等待上游能力。

替代方案：

- 只写能力清单：容易漏项，无法验收。
- 强制真实接入所有供应商：对个人版不现实，也会引入大量凭据和基础设施成本。

### Decision 3: 保持 Harness 作为工程底座

个人版 SHALL 继续围绕 AgentScope Java v2/Harness 构建，使用其 workspace、memory、filesystem、sandbox、subagent、skill、plan mode、channel、compaction 和 AgentState 能力承载长期任务。

替代方案：

- 手写个人 Agent runtime：短期灵活，但会背离用户要求的 AgentScope Java v2 完整功能。
- 只做聊天 SDK 包装：无法覆盖工具、记忆、多 Agent、工作区和长期任务。

### Decision 4: 工具与技能采用个人授权，而不是企业审批

工具和技能 SHALL 使用个人授权模型：默认本地只读工具可直接执行，高风险工具、外部写操作、代码执行、Shell、数据库写入和网络副作用 MUST 触发用户确认或沙箱策略。个人授权结果 SHALL 可记忆、可撤销、可审计。

替代方案：

- 放开所有个人工具：体验快但风险高。
- 维持企业审批人流程：对个人版过重。

### Decision 5: Web 控制台转为个人工作台

前端 SHALL 从企业管理/运维视图调整为个人 Agent 工作台，优先支持聊天、任务、工作区文件、知识/记忆、工具、技能、trace、模型和本地诊断。重复的企业报表、租户管理、角色授权和发布门禁视图 SHALL 被移除、隐藏或降级为开发诊断。

替代方案：

- 保留现有企业控制台并改文案：实现快，但用户心智不一致。
- 删除前端只保留 API：无法满足个人完整 Agent 的可操作性。

### Decision 6: 集成层本地优先、可插拔扩展

memory、RAG、session/state、skill repository、protocol、ecosystem 和 infrastructure 集成 SHALL 采用本地优先实现，并通过 provider abstraction 支持后续接入 Bailian、Mem0、ReMe、Dify、Haystack、RAGFlow、Redis、MySQL、OSS、Git、A2A、AG-UI、Nacos、Higress 等官方列出的集成方向。

替代方案：

- 首版只绑定一个云厂商：简单但不可移植。
- 全部使用内存实现：开发快但无法覆盖会话恢复和长期个人记忆。

## Risks / Trade-offs

- [企业代码惯性导致个人版概念不纯] -> 在 API、UI 和文档中显式移除企业租户、角色、审批人和运维报表作为主流程。
- [“全部功能”范围过大] -> 用官方页面覆盖矩阵分层验收，区分真实实现、适配接口、本地 mock 和非目标。
- [个人工具权限过松] -> 对副作用工具强制 HITL、参数展示、沙箱和撤销授权。
- [本地文件和记忆泄露隐私] -> 工作区路径隔离，敏感内容不写普通日志，支持删除和导出。
- [多 Agent 和技能导致上下文膨胀] -> 使用 compaction、plan mode、channel 和 artifact 落盘控制上下文。
- [外部集成凭据复杂] -> provider 配置必须支持未配置状态，未配置时 UI 展示可用性和替代本地实现。
- [从企业 MVP 迁移时破坏现有测试] -> 先建立个人版 OpenSpec，再按任务逐步调整测试和 UI。

## Migration Plan

1. 建立个人版配置 profile，保留兼容 context key，但默认映射为个人 owner/workspace。
2. 收敛 API 和前端主导航，将企业管理/运维入口改为个人工作台入口。
3. 先实现个人 Agent core、流式事件、会话状态和模型 provider 配置。
4. 接入个人 workspace、filesystem、sandbox、compaction、plan mode 和 channel。
5. 接入个人 memory/RAG，本地知识库优先，保留外部 provider 扩展。
6. 接入个人工具、HITL、MCP/协议入口、技能仓库和多 Agent 编排。
7. 建立 AgentScope Java v2 覆盖矩阵，逐页标记实现、测试、文档和剩余缺口。
8. 更新 `docs/`、`web/README.md`、测试和发布说明，移除企业平台主目标表述。

回滚策略：保留现有企业 MVP 分支或变更记录；每个阶段通过配置开关禁用个人版新入口、切回原聊天 API 或隐藏工作台模块。状态迁移必须保留原会话、工具审计和知识数据，不执行破坏性删除。

## Open Questions

- 个人版默认模型提供方优先支持 DashScope、OpenAI-compatible、本地 Ollama，还是全部作为同等 provider？
- 个人工作区默认存储目录是否放在应用数据目录、项目目录，还是用户可配置路径？
- 沙箱首版选择本地子进程、Docker，还是仅接口预留加 mock？
- 是否需要保留企业版 API 兼容层，还是允许破坏性移除企业租户字段？
- 外部协议优先支持 A2A、AG-UI、Chat Completions Web 中的哪一个作为首发？
