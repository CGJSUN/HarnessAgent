## Context

`docs/start.md` 定义了一条基于 AgentScope Java v2 建设企业级 Agent 的路径：先用 Spring Boot + HarnessAgent 跑通 MVP，再逐步补齐知识库/RAG、受治理的工具调用、生产运行时，最后演进到多 Agent 平台。当前仓库没有应用代码，也没有既有 specs，因此本变更是在建立第一版需求基线，而不是修改已有实现。

主要干系人包括普通企业用户、业务专家、审核人、管理员、平台运维以及安全/合规团队。设计需要让第一阶段足够小、能交付，同时保留迁移到分布式生产部署的路径。

目标平台形态如下：

```text
用户入口
  Web / 企业 IM / API 网关 / 内部系统
        |
        v
Spring Boot API 层
  chat, sessions, messages, admin, ops
        |
        v
Agent 平台服务
  Agent Core | RAG | 工具治理 | 多 Agent 编排
        |
        v
HarnessAgent + AgentScope Java v2
  ModelProvider | RuntimeContext | Workspace | Memory | Skills
        |
        v
基础设施
  Redis/MySQL | Remote FS/Sandbox | Vector/RAG Provider | OTel | IdP
```

## Goals / Non-Goals

**Goals:**

- 将企业级 Agent 平台拆成可测试、可验收的 OpenSpec 需求。
- 将 MVP 控制在一个可靠 Agent 的闭环内：聊天 API、HarnessAgent builder、模型配置、会话隔离和流式响应。
- 明确后续阶段的能力契约：RAG、受治理的工具调用、生产运行时、安全治理、管理/运营控制台和多 Agent 编排。
- 保留生产迁移路径：分布式状态、远程文件系统、沙箱、可观测性、限流和模型 fallback。
- 让高风险操作具备审计、确认或审批能力。

**Non-Goals:**

- 本变更不实现应用代码。
- 第一阶段 MVP 不要求接入 RAG、企业工具、多 Agent Supervisor、完整管理后台或生产级分布式存储。
- 生产环境不允许 Agent 未经审核自动提升或修改 Skill。
- 代码执行、Shell 执行、数据库写入、邮件发送和审批提交不被视为默认低风险行为。

## Decisions

### Decision 1: 使用 HarnessAgent 作为 Agent 工程入口

使用 HarnessAgent 作为核心运行抽象，因为它把工作区、长期记忆、会话持久化、子 Agent、沙箱和压缩等工程能力封装在 builder 风格 API 后面。

替代方案：

- 直接组装底层 AgentScope 原语：控制力更强，但在 MVP 前会引入更多平台代码和集成风险。
- 先包装第三方聊天 SDK：纯聊天更快，但与 `docs/start.md` 中 AgentScope Java v2 的生产路径不一致。

### Decision 2: 按可独立验收的产品能力拆分 specs

OpenSpec 能力按产品和运行边界拆分，而不是按 Java 包名拆分：

- `enterprise-agent-core`
- `knowledge-rag`
- `governed-tool-calling`
- `production-runtime`
- `security-governance`
- `admin-ops-console`
- `multi-agent-orchestration`

这样 MVP 可以独立交付，后续治理能力也能被明确验收。

替代方案：

- 使用一个大的 `enterprise-agent-platform` spec：文件更少，但难以分阶段验收。
- 只按技术组件拆 spec：对工程师方便，但对产品验收和企业 rollout 不够清晰。

### Decision 3: 每次调用必须携带 tenant/user/agent/session 运行上下文

每次 Agent 调用都必须派生：

```text
RuntimeContext.userId    = tenantId + ":" + userId
RuntimeContext.sessionId = agentId + ":" + sessionId
```

这样可以避免 MVP 代码写死单用户或单 Agent 假设，并为多租户生产隔离留下直接路径。

替代方案：

- 只使用 userId 和 sessionId：更简单，但多租户和多 Agent 后语义不够明确。
- 只把 tenant 和 Agent 放在请求 metadata：API 更轻，但下游状态 key 更容易遗漏这些维度。

### Decision 4: 采用分阶段存储策略

开发期和 MVP 可以使用本地文件状态以快速迭代。生产和多副本部署必须使用 RedisDistributedStore 或 MysqlDistributedStore，不能继续依赖本地 JsonFileAgentStateStore。

工作区策略按工作负载风险区分：

- 普通知识/办公类 Agent 使用 RemoteFilesystemSpec。
- 涉及代码、Shell、SQL 或不可信执行的 Agent 使用带隔离控制的沙箱文件系统。
- OSS/S3/MinIO/JDBC 快照存储用于较大的沙箱状态，不用于高频小 KV。

替代方案：

- 一开始只支持 Redis/MySQL：更接近生产，但会拖慢首个 MVP。
- 用对象存储承载所有状态：不适合高频小状态更新。

### Decision 5: 企业工具默认先只读，写操作后置治理

企业集成优先以只读工具引入。高风险工具，例如发邮件、提交审批、修改数据库、创建工单或运行脚本，必须具备权限检查、参数校验、用户确认或审核人审批、幂等控制和审计记录。

替代方案：

- 依赖 prompt 约束放开所有工具：不可接受，因为 Prompt Injection 可能间接影响工具调用。
- 永久禁止所有写工具：安全但无法满足流程自动化目标。

### Decision 6: 把 RAG 当作受治理的知识访问，而不是简单检索

RAG 必须覆盖知识源接入、解析、切片、索引、版本、引用来源、删除失效和权限过滤。当检索证据不足或用户无权访问时，系统必须提供无法回答或澄清路径。

替代方案：

- 只加向量检索：演示快，但会留下企业信任与合规缺口。
- 立即绑定单一 RAG 提供方：集成简单，但限制未来接入 Simple、百炼、Dify、HayStack、RAGFlow 等方案。

### Decision 7: 可观测性和审计属于验收标准

平台必须记录模型调用、工具调用、RAG 检索、Token 用量、耗时、错误、用户反馈、确认和审批。生产可观测方向是 OpenTelemetry。

替代方案：

- 只依赖 HTTP 日志：不足以诊断模型、工具和 RAG 问题。
- 到处保存完整原始日志：便于调试，但会带来敏感数据暴露风险。

## Risks / Trade-offs

- [平台目标导致范围膨胀] -> 按阶段交付，第一阶段只做核心聊天、会话隔离和流式响应。
- [租户或权限数据泄漏] -> RuntimeContext 必须包含 tenant/user/agent/session，RAG 和工具必须做权限过滤。
- [工具错误修改业务系统] -> 工具分级、结构化参数校验、确认/审批、幂等和审计必须前置。
- [RAG 回答不可追溯] -> 必须返回引用来源、保留文档版本、支持删除失效和无答案行为。
- [本地 MVP 假设泄漏到生产] -> 明确 production-runtime 要求，多副本生产必须使用 DistributedStore。
- [日志暴露敏感 prompt 或业务数据] -> 要求日志脱敏、密钥托管、最小权限和审计留存策略。
- [模型或供应商不稳定] -> 定义 ModelProvider 抽象、限流、Token 预算、fallback 和失败分类。
- [Skill 治理失控] -> 生产 Skill 必须有版本、审核、发布/禁用和回滚能力。

## Migration Plan

1. Bootstrap MVP：Spring Boot、HarnessAgent、单一 ModelProvider、本地开发状态、聊天 API、会话隔离和流式响应。
2. 增加知识库/RAG：文档生命周期、引用来源、权限过滤、反馈和检索指标。
3. 增加受治理工具调用：先接只读企业工具，再接需要确认或审批的高风险工具。
4. 加固生产运行时：Redis/MySQL DistributedStore、RemoteFilesystem 或 Sandbox、快照、OpenTelemetry、限流和 fallback。
5. 增加安全治理与管理/运营控制台：身份、权限、配置、审计、指标和成本。
6. 在核心、RAG、工具、运行时和治理稳定后，再增加多 Agent 编排。

回滚策略按阶段处理：通过配置禁用新工具或 Agent、切回旧模型提供方、关闭 RAG-backed answer，或将流量回退到原单 Agent runtime。生产回滚必须保留审计记录。

## Open Questions

- 第一批业务场景应选择知识助手、流程助手、客服助手、数据分析助手、代码/运维助手，还是办公自动化助手？
- 权威身份源是什么：SSO、OAuth2、LDAP、飞书、钉钉、企微，还是内部账号系统？
- 首发模型提供方是谁，是否有数据不出境或私有化部署要求？
- 会话和长期记忆留存应按租户策略、Agent 策略，还是用户可配置策略管理？
- 首发 RAG 提供方是什么，知识源权限如何同步？
- 第一批接入的 3-5 个企业系统是什么？
- 哪些操作需要审核人审批，哪些只需要用户本人确认？
- 审计留存、脱敏和导出要求如何满足合规？
