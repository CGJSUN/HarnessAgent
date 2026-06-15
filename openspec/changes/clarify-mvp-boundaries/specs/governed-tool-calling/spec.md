## MODIFIED Requirements

### Requirement: MCP 工具接入
系统 SHALL 支持将 MCP 标记为受治理的工具来源类型，并对这类工具应用同样的注册、权限、参数校验、确认、幂等和审计控制。当前 MVP SHALL NOT 将该能力解释为已经接入真实外部 MCP client/server 执行；真实连接、调用、超时、重试、错误映射和结果脱敏适配 SHALL 通过后续 OpenSpec 变更引入。

#### Scenario: 注册 MCP 来源工具
- **WHEN** 管理员注册 `sourceType=MCP` 的工具
- **THEN** 系统 SHALL 保存工具来源类型、source ref、权限策略、参数 schema、风险等级和审计策略
- **AND** 后续执行 SHALL 继续经过平台工具治理链路

#### Scenario: 治理 MCP 来源工具
- **WHEN** Agent 尝试执行 MCP 来源工具
- **THEN** 系统 SHALL 在执行前应用权限、参数、确认、幂等和审计策略
- **AND** 当前默认执行器 MAY 只返回受治理的占位或回显结果

#### Scenario: 未来接入真实 MCP Server
- **WHEN** 团队决定调用真实 MCP Server 提供的工具
- **THEN** 团队 SHALL 创建独立 OpenSpec 变更
- **AND** 该变更 SHALL 定义 MCP client 配置、连接安全、超时重试、错误映射、结果脱敏、幂等语义和审计字段
