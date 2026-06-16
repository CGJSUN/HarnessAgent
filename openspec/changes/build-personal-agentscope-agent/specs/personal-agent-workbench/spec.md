## ADDED Requirements

### Requirement: 个人聊天工作台
工作台 SHALL 提供个人聊天界面，支持流式文本、类型化事件、文件附件、引用来源、工具确认和子 Agent 状态。

#### Scenario: 处理等待确认的工具
- **WHEN** Agent 产生等待确认的工具事件
- **THEN** 工作台 SHALL 展示操作摘要、参数和确认/拒绝操作

### Requirement: 任务和计划视图
工作台 SHALL 展示计划模式生成的任务计划、执行进度、阻塞点和完成状态。

#### Scenario: 查看长任务计划
- **WHEN** 用户打开一个长任务会话
- **THEN** 工作台 SHALL 展示计划文件、当前步骤、已完成步骤和下一步

### Requirement: 工作区文件视图
工作台 SHALL 提供个人工作区文件浏览、预览、上传、下载、删除和引用定位能力。

#### Scenario: 打开 Agent 生成文件
- **WHEN** Agent 生成一个工作区文件
- **THEN** 工作台 SHALL 在文件视图中展示该文件
- **AND** 用户 SHALL 能从消息引用跳转到该文件

### Requirement: 知识和记忆管理
工作台 SHALL 允许用户查看、添加、删除、导出和重建个人知识源与记忆。

#### Scenario: 删除个人记忆
- **WHEN** 用户删除一条个人记忆
- **THEN** 后续检索 SHALL NOT 返回该记忆
- **AND** 工作台 SHALL 展示删除结果

### Requirement: 工具和技能管理
工作台 SHALL 允许用户启用、禁用、配置、授权和测试个人工具与技能。

#### Scenario: 禁用高风险工具
- **WHEN** 用户禁用某个高风险工具
- **THEN** Agent SHALL NOT 再执行该工具
- **AND** 工作台 SHALL 在工具列表中展示禁用状态

### Requirement: Agent 配置
工作台 SHALL 允许用户配置 Agent 名称、system prompt、模型 provider、预算、工作区策略、记忆策略、工具和技能。

#### Scenario: 更新模型配置
- **WHEN** 用户修改某个 Agent 的模型 provider
- **THEN** 后续该 Agent 会话 SHALL 使用新的模型配置
- **AND** 工作台 SHALL 保存配置变更记录

### Requirement: Trace 和诊断
工作台 SHALL 展示个人 Agent 执行 trace、模型调用、工具调用、RAG 检索、子 Agent 调用、错误和诊断状态。

#### Scenario: 调试一次失败执行
- **WHEN** 用户打开失败会话的 trace
- **THEN** 工作台 SHALL 展示失败步骤、错误类型、相关工具或模型调用和可操作诊断信息

### Requirement: 响应式工作界面
工作台 SHALL 在桌面和移动视口中保持聊天、任务、文件、工具、技能和 trace 主要工作流可用。

#### Scenario: 移动端查看聊天
- **WHEN** 用户在移动视口打开工作台
- **THEN** 聊天、工具确认和文件引用 SHALL 保持可读且不遮挡主要操作
