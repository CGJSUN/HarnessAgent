## MODIFIED Requirements

### Requirement: 工具管理
系统 SHALL 允许授权管理员查看已注册企业工具、查看分类和权限摘要，并启用或禁用工具。完整工具注册、参数 schema 编辑和授权策略编辑 SHALL 继续由 `/api/tools` 通用后端 API 或后续控制台变更承载，当前 console REST/UI 不将这些编辑能力声明为可验收范围。

#### Scenario: 禁用风险工具
- **WHEN** 管理员禁用某个已注册工具
- **THEN** Agent SHALL NOT 执行该工具，直到授权管理员重新启用

#### Scenario: 查看工具授权摘要
- **WHEN** 管理员打开控制台工具管理视图
- **THEN** 控制台 SHALL 展示后端返回的已注册工具、启用状态、风险等级和权限摘要
- **AND** 控制台 SHALL NOT 暗示完整注册、参数 schema 编辑或授权策略编辑已在 console REST/UI 中开放

### Requirement: 知识库管理
系统 SHALL 允许授权管理员或业务专家在控制台查看知识源、版本、权限、索引状态和撤销动作。通用知识删除 API 或服务方法的存在 SHALL NOT 被解释为 console 专用 delete REST/UI 已开放。

#### Scenario: 查看索引状态
- **WHEN** 授权用户查看某个知识源
- **THEN** 控制台 SHALL 展示索引状态、版本、可见范围和最近同步结果

#### Scenario: 撤销知识源
- **WHEN** 授权管理员在控制台撤销知识源
- **THEN** 系统 SHALL 阻止该知识源继续用于未来检索和引用
- **AND** 控制台 SHALL 记录或展示后端返回的撤销状态

#### Scenario: 删除动作未开放
- **WHEN** 控制台展示知识源管理动作
- **THEN** 控制台 SHALL NOT 将 hard delete 呈现为当前可执行动作
- **AND** 文档 SHALL 说明 console 专用 delete REST/UI 当前未开放

### Requirement: Skill 管理
系统 SHALL 允许授权管理员在控制台查看 Skill 仓库和版本，并执行当前已开放的审批、发布和禁用动作。Skill rollback SHALL 被描述为发布预案或底层服务能力，当前 console REST/UI 不提供可执行 rollback 动作。

#### Scenario: 发布 Skill 版本
- **WHEN** 管理员发布已审批的 Skill 版本
- **THEN** 平台 SHALL 将该版本标记为可用于生产 Agent
- **AND** 系统 SHALL 记录发布事件

#### Scenario: 禁用 Skill 版本
- **WHEN** 管理员禁用某个 Skill 版本
- **THEN** 平台 SHALL 阻止该版本继续被新请求使用
- **AND** 系统 SHALL 记录禁用事件

#### Scenario: 回滚动作未开放
- **WHEN** 管理员打开控制台 Skill 管理
- **THEN** 控制台 MAY 展示 rollback 作为未来动作或发布预案提示
- **AND** 控制台 SHALL NOT 将 rollback 呈现为当前可执行 REST/UI 动作
