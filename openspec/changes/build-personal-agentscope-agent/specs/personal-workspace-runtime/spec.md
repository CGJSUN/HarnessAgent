## ADDED Requirements

### Requirement: 个人工作区结构
系统 SHALL 为每个 owner 和 agent 创建个人工作区，并以文件或数据库元数据记录 persona、知识、技能、子 Agent 规格、计划和会话日志。

#### Scenario: 初始化个人工作区
- **WHEN** 用户首次启用某个 Agent
- **THEN** 系统 SHALL 创建该 Agent 的个人工作区
- **AND** 工作区 SHALL 包含可定位 persona、memory、skills、subagents、plans 和 sessions 的结构

### Requirement: 文件系统边界
系统 SHALL 限制 Agent 只能访问授权工作区路径，并阻止路径穿越访问个人工作区外的文件。

#### Scenario: 工具尝试访问工作区外文件
- **WHEN** Agent 工具请求读取未授权路径
- **THEN** 系统 SHALL 拒绝该文件访问
- **AND** 拒绝事件 SHALL 记录到执行轨迹

### Requirement: 沙箱执行
系统 SHALL 对代码、Shell、SQL 或不可信工具执行使用沙箱策略，并支持本地子进程、Docker 或远端沙箱适配。

#### Scenario: 执行高风险命令
- **WHEN** Agent 请求执行 Shell 命令
- **THEN** 系统 SHALL 在沙箱策略允许且用户确认后执行
- **AND** 命令 SHALL NOT 直接运行在不受控的应用进程权限下

### Requirement: 快照和恢复
系统 SHALL 支持个人工作区和沙箱状态快照，并能在应用重启或长任务恢复时重新挂载。

#### Scenario: 恢复长任务工作区
- **WHEN** 长任务在执行中断后恢复
- **THEN** 系统 SHALL 恢复相关工作区文件、计划状态和沙箱快照引用

### Requirement: 上下文压缩
系统 SHALL 在上下文接近模型限制时执行结构化压缩，并保留目标、当前状态、关键发现、决策、文件引用和下一步。

#### Scenario: 对话上下文过长
- **WHEN** 会话上下文超过配置阈值
- **THEN** 系统 SHALL 生成结构化摘要并替换低价值上下文
- **AND** 系统 SHALL 保留可追溯的原始文件或消息引用

### Requirement: Plan Mode
系统 SHALL 支持只读计划模式，将复杂任务计划持久化到个人工作区，并在执行模式中按计划推进。

#### Scenario: 创建执行计划
- **WHEN** 用户要求 Agent 先规划复杂任务
- **THEN** 系统 SHALL 进入计划模式并生成计划文件
- **AND** 计划模式 SHALL NOT 执行会产生副作用的工具

### Requirement: Channel 分层
系统 SHALL 支持按 channel 区分用户可见消息、内部推理摘要、工具事件、计划更新和系统提醒。

#### Scenario: 展示用户可见输出
- **WHEN** 前端渲染一次 Agent 执行
- **THEN** 前端 SHALL 只默认展示用户可见 channel
- **AND** trace 视图 SHALL 在需要时展示工具事件和计划更新 channel
