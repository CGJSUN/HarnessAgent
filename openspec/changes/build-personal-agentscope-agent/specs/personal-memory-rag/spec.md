## ADDED Requirements

### Requirement: 分层个人记忆
系统 SHALL 支持分层个人记忆，包括会话上下文、Agent 自维护记忆文件和事实流水账。

#### Scenario: 写入长期偏好
- **WHEN** 用户要求 Agent 记住长期偏好
- **THEN** 系统 SHALL 将该偏好写入个人记忆层
- **AND** 后续会话 SHALL 能在权限允许时检索该偏好

### Requirement: 记忆工具
系统 SHALL 提供显式记忆读写工具，并对写入内容执行用户可见确认或可撤销记录。

#### Scenario: Agent 请求写入记忆
- **WHEN** Agent 计划保存新的个人事实
- **THEN** 系统 SHALL 展示写入摘要
- **AND** 用户 SHALL 能确认、拒绝或之后删除该记忆

### Requirement: 个人知识源接入
系统 SHALL 支持个人文件、目录或 URL 知识源接入，并记录来源、版本、索引状态和可见范围。

#### Scenario: 添加本地文档
- **WHEN** 用户添加一个受支持的本地文档
- **THEN** 系统 SHALL 解析、切片、索引该文档
- **AND** 知识源 SHALL 关联到个人 owner 和版本

### Requirement: 检索增强回答
系统 SHALL 在需要知识支持的回答中检索个人知识源和记忆，并返回引用来源。

#### Scenario: 使用个人文档回答
- **WHEN** 用户询问个人知识库中的内容
- **THEN** Agent SHALL 使用可访问片段生成回答
- **AND** 响应 SHALL 包含文档标题、片段、路径或版本等来源信息

### Requirement: 无答案策略
当个人知识或记忆没有足够证据时，系统 SHALL 返回无法确定、要求澄清或说明需要更多资料。

#### Scenario: 没有可访问证据
- **WHEN** 检索没有找到足够证据
- **THEN** Agent SHALL NOT 编造引用来源
- **AND** 响应 SHALL 明确说明当前资料无法确定答案

### Requirement: 可插拔记忆和 RAG 后端
系统 SHALL 通过 provider abstraction 支持本地实现和外部 memory/RAG provider。

#### Scenario: 切换 RAG provider
- **WHEN** 用户将知识库 provider 从本地实现切换为外部 provider
- **THEN** 系统 SHALL 保持统一的索引状态、检索结果和引用来源契约

### Requirement: 个人数据删除和导出
系统 SHALL 支持用户删除和导出个人记忆、知识源、索引元数据和引用记录。

#### Scenario: 删除个人知识源
- **WHEN** 用户删除某个知识源
- **THEN** 系统 SHALL 阻止该知识源片段继续参与未来检索
- **AND** 系统 SHALL 记录删除结果供用户查看
