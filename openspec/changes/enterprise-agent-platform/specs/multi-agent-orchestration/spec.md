## ADDED Requirements

### Requirement: 专家 Agent 注册表
系统 SHALL 支持注册专家 Agent，并记录用途、输入契约、输出契约、权限、工具、知识访问和归属人元数据。

#### Scenario: 注册数据分析 Agent
- **WHEN** 管理员注册专家 Agent
- **THEN** 注册表 SHALL 保存该 Agent 的用途、契约、权限、启用工具、知识访问和归属人

### Requirement: Supervisor 路由
系统 SHALL 支持 Supervisor Agent 根据任务意图、权限范围和 Agent 能力将任务路由给专家 Agent。

#### Scenario: 路由知识问题
- **WHEN** 用户提出制度类问题
- **THEN** Supervisor SHALL 在权限允许时将任务路由给具备知识能力的 Agent

### Requirement: Agent as Tool
系统 SHALL 允许经过批准的子 Agent 作为工具暴露给父 Agent。

#### Scenario: 父 Agent 调用审批 Agent
- **WHEN** 父 Agent 委派审批相关任务
- **THEN** 子 Agent 调用 SHALL 遵循已注册的工具契约和平台权限策略

### Requirement: 任务规划和交接
系统 SHALL 支持复杂任务的 planning、pipeline execution、routing 和 handoffs，并保留可追踪性。

#### Scenario: 多步骤客服任务
- **WHEN** 任务需要知识查询、客户查询和工单创建
- **THEN** 系统 SHALL 记录计划、每个 Agent 或工具步骤以及交接结果

### Requirement: 跨 Agent 上下文边界
系统 SHALL 控制父 Agent 和子 Agent 之间共享哪些上下文、记忆、文件和工具输出。

#### Scenario: 子 Agent 接收委派上下文
- **WHEN** 父 Agent 向子 Agent 委派任务
- **THEN** 系统 SHALL 只传递子 Agent 契约和用户权限允许的上下文

### Requirement: 编排可观测性
系统 SHALL 追踪 Supervisor 决策、子 Agent 调用、工具调用、handoffs、失败和最终回答组装。

#### Scenario: 调试错误路由
- **WHEN** 运维人员调查一次错误路由决策
- **THEN** trace SHALL 在权限范围内展示 Supervisor 决策、候选 Agent、选中 Agent 和相关执行结果

### Requirement: 人工干预
当编排置信度低、策略阻止操作或高风险步骤需要复核时，系统 SHALL 支持人工干预或升级。

#### Scenario: Supervisor 无法选择安全路径
- **WHEN** Supervisor 无法确定一个被允许且有足够置信度的下一步
- **THEN** 系统 SHALL 按策略要求澄清、升级给人工或停止
