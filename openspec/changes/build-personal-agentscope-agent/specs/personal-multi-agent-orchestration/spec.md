## ADDED Requirements

### Requirement: 个人子 Agent 规格
系统 SHALL 支持在工作区中声明个人子 Agent 规格，包括名称、用途、输入、输出、工具、技能和上下文边界。

#### Scenario: 注册写作子 Agent
- **WHEN** 用户添加一个写作子 Agent 规格
- **THEN** 系统 SHALL 保存其用途、输入输出契约、可用工具和可访问上下文

### Requirement: Supervisor 路由
系统 SHALL 支持 Supervisor 根据任务意图、能力描述、上下文边界和用户授权选择子 Agent。

#### Scenario: 路由到研究子 Agent
- **WHEN** 用户任务需要资料检索和总结
- **THEN** Supervisor SHALL 在授权范围内选择具备研究能力的子 Agent

### Requirement: Subagent 后台委派
系统 SHALL 支持同步和后台子 Agent 委派，并在后台任务终态通过系统提醒或事件返回主会话。

#### Scenario: 后台子 Agent 完成
- **WHEN** 后台子 Agent 完成委派任务
- **THEN** 系统 SHALL 将完成状态和结果摘要发送回主会话
- **AND** 主 Agent SHALL 能基于结果继续执行

### Requirement: Agent-as-Tool
系统 SHALL 允许父 Agent 将经过配置的子 Agent 作为工具调用，并保留工具契约、权限和 trace。

#### Scenario: 父 Agent 调用代码审查子 Agent
- **WHEN** 父 Agent 以工具形式调用代码审查子 Agent
- **THEN** 系统 SHALL 记录输入、输出、权限决策和子 Agent trace 引用

### Requirement: Handoff 和上下文边界
系统 SHALL 控制父 Agent、子 Agent 和 handoff 目标之间共享的消息、文件、记忆和工具结果。

#### Scenario: 子 Agent 不应看到私密记忆
- **WHEN** 父 Agent 委派任务给无记忆权限的子 Agent
- **THEN** 系统 SHALL NOT 传递受限个人记忆或私密文件内容

### Requirement: 多 Agent 执行轨迹
系统 SHALL 记录 Supervisor 决策、候选 Agent、选中 Agent、handoff、工具调用、失败和最终结果组装。

#### Scenario: 调试错误路由
- **WHEN** 用户查看一次多 Agent 任务 trace
- **THEN** 系统 SHALL 展示路由原因、子 Agent 调用链和最终回答来源

### Requirement: 编排失败处理
当 Supervisor 无法安全选择下一步或子 Agent 失败时，系统 SHALL 澄清、重试、降级到主 Agent 或停止执行。

#### Scenario: 子 Agent 执行失败
- **WHEN** 子 Agent 返回不可恢复错误
- **THEN** 系统 SHALL 将失败原因汇总到主 Agent
- **AND** 主 Agent SHALL 按策略选择重试、降级或向用户说明失败
