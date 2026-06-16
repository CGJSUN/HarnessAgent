## ADDED Requirements

### Requirement: 技能仓库
系统 SHALL 支持本地技能仓库，并通过适配器扩展到 Git、MySQL 或 PostgreSQL 技能仓库。

#### Scenario: 加载本地技能
- **WHEN** 应用启动或用户刷新技能仓库
- **THEN** 系统 SHALL 扫描本地技能目录并读取技能元数据

### Requirement: 技能元数据
系统 SHALL 为每个技能记录名称、描述、触发条件、版本、来源、权限、依赖资源和适用 Agent。

#### Scenario: 展示技能详情
- **WHEN** 用户查看某个技能
- **THEN** 工作台 SHALL 展示该技能的元数据、版本、来源和所需权限

### Requirement: 技能加载和执行
系统 SHALL 根据用户意图、Agent 配置和触发规则加载技能，并在执行前注入必要指令和资源。

#### Scenario: 触发文件分析技能
- **WHEN** 用户提出与已安装技能匹配的文件分析任务
- **THEN** Agent SHALL 加载该技能指令
- **AND** 技能执行 SHALL 受工作区和工具权限限制

### Requirement: 技能权限
系统 SHALL 将技能请求的文件、工具、网络、沙箱和记忆权限纳入个人授权决策。

#### Scenario: 技能请求网络访问
- **WHEN** 技能需要访问外部网络
- **THEN** 系统 SHALL 在执行前检查用户授权和网络策略

### Requirement: 技能版本管理
系统 SHALL 支持技能启用、禁用、升级、回滚和版本锁定。

#### Scenario: 回滚技能版本
- **WHEN** 用户将技能回滚到旧版本
- **THEN** 后续 Agent 执行 SHALL 使用被选定的技能版本
- **AND** 系统 SHALL 记录版本变更事件

### Requirement: 技能验证
系统 SHALL 提供技能验证入口，检查元数据、引用文件、依赖工具和基本执行样例。

#### Scenario: 安装无效技能
- **WHEN** 用户安装缺少必需元数据的技能
- **THEN** 系统 SHALL 拒绝启用该技能
- **AND** 工作台 SHALL 展示可修复的验证错误
