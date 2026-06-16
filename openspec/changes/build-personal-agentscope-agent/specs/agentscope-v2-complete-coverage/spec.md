## ADDED Requirements

### Requirement: 官方文档覆盖矩阵
系统 SHALL 维护 AgentScope Java v2 中文文档覆盖矩阵，并至少覆盖快速开始、核心组件、Harness、参考和集成五类页面。

#### Scenario: 查看覆盖矩阵
- **WHEN** 开发者打开覆盖矩阵文档或控制台诊断页
- **THEN** 系统 SHALL 展示每个官方文档页面对应的项目 capability、实现状态、测试状态和文档链接

### Requirement: 核心组件覆盖
覆盖矩阵 SHALL 包含 Agent、Message/Event、Middleware、Model、Permission System、Tool、Context/AgentState 核心组件。

#### Scenario: 校验核心组件项
- **WHEN** 运行 AgentScope v2 覆盖校验
- **THEN** 校验 SHALL 确认每个核心组件都有对应 spec、实现任务或明确非目标说明

### Requirement: Harness 能力覆盖
覆盖矩阵 SHALL 包含 architecture、workspace、filesystem、sandbox、memory、subagent、skill、plan mode、channel 和 compaction。

#### Scenario: 校验 Harness 项
- **WHEN** 运行 AgentScope v2 覆盖校验
- **THEN** 校验 SHALL 确认每个 Harness 页面都映射到个人版运行时、工作区、多 Agent、技能或记忆能力

### Requirement: 集成能力覆盖
覆盖矩阵 SHALL 包含 memory、RAG、session/state、skill repository、protocol、ecosystem 和 infrastructure 集成方向。

#### Scenario: 校验集成项
- **WHEN** 某个官方集成尚未真实接入
- **THEN** 覆盖矩阵 SHALL 标记本地实现、provider interface、mock、未配置状态或后续扩展计划

### Requirement: 版本同步
系统 SHALL 记录覆盖矩阵所依据的官方文档 URL、版本和最后更新时间，并在文档更新时间变化时要求重新评估。

#### Scenario: 官方文档更新时间变化
- **WHEN** 覆盖校验发现官方索引的最后更新时间晚于本地记录
- **THEN** 系统 SHALL 将 AgentScope v2 覆盖状态标记为需要复核

### Requirement: 完整覆盖声明门禁
系统 MUST NOT 声明“AgentScope Java v2 全部功能已覆盖”，除非覆盖矩阵中所有必选页面均有实现、测试和文档状态。

#### Scenario: 存在未覆盖页面
- **WHEN** 覆盖矩阵中存在状态为未评估或未覆盖的必选页面
- **THEN** 发布门禁 SHALL 拒绝完整覆盖声明
