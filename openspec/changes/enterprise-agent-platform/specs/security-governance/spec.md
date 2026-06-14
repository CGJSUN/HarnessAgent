## ADDED Requirements

### Requirement: 企业身份接入
系统 SHALL 通过已批准的企业身份提供方认证用户，例如 SSO、OAuth2、LDAP、飞书、钉钉、企微或内部身份服务。

#### Scenario: 已认证聊天请求
- **WHEN** 用户调用 Agent API
- **THEN** 系统 SHALL 将请求关联到已认证的企业身份和租户

### Requirement: RBAC 和资源权限
系统 SHALL 对 Agent、工具、知识源、管理操作和运营数据执行基于角色的权限控制。

#### Scenario: 非管理员修改 Agent 配置
- **WHEN** 非管理员用户尝试修改 Agent 配置
- **THEN** 系统 SHALL 拒绝该操作

### Requirement: 数据权限一致性
系统 SHALL 防止用户通过 RAG、工具、记忆、文件或审计视图访问受限业务数据。

#### Scenario: 工具返回受限数据
- **WHEN** 工具结果包含超出用户权限范围的数据
- **THEN** 系统 SHALL 按数据权限策略阻止或过滤该结果

### Requirement: Prompt 和工具安全
系统 SHALL 应用防 Prompt Injection 和不安全工具调用的控制。

#### Scenario: 检索内容要求忽略策略
- **WHEN** 检索知识或用户输入包含与平台安全或权限策略冲突的指令
- **THEN** 系统 SHALL 保持平台策略
- **AND** 系统 SHALL NOT 扩大工具或数据访问权限

### Requirement: 密钥管理
系统 SHALL 将模型 key、企业系统凭证和工具 token 存储在已批准的配置或密钥存储中，且 SHALL NOT 将其硬编码到应用代码。

#### Scenario: 配置模型 API key
- **WHEN** 运维人员配置模型 API key
- **THEN** key SHALL 从配置或密钥存储加载
- **AND** key SHALL NOT 被提交到源代码

### Requirement: 敏感数据脱敏
系统 SHALL 按策略对日志、trace、审计记录和导出的诊断信息中的敏感数据进行脱敏。

#### Scenario: 工具输出包含个人信息
- **WHEN** 工具输出包含敏感个人或业务数据
- **THEN** 持久化日志和 trace SHALL 按策略脱敏或最小化敏感字段

### Requirement: 高风险操作审计
系统 SHALL 留存高风险工具调用、审批、模型响应、用户确认和人工干预的审计记录。

#### Scenario: 审核审批提交
- **WHEN** 审计人员复核一次已提交的审批操作
- **THEN** 系统 SHALL 在审计权限范围内提供操作者、审批人、参数、时间戳、结果和关联会话引用

### Requirement: Skill 治理
系统 SHALL 对生产 Skill 提供审核、版本、发布控制、禁用控制和回滚支持。

#### Scenario: 发布新 Skill 版本
- **WHEN** 某个 Skill 版本被提议用于生产
- **THEN** 系统 SHALL 在该 Skill 对 Agent 可用前要求审批通过
