## MODIFIED Requirements

### Requirement: 文档处理流水线
系统 SHALL 对知识文档执行上传或同步、解析、切片、轻量索引和版本追踪。当前 MVP 的轻量索引 MAY 使用 token 化和词法评分；真实 embedding 生成、向量索引和向量数据库写入 SHALL 通过后续 OpenSpec 变更引入。

#### Scenario: 索引上传的制度文档
- **WHEN** 用户上传受支持的制度文档
- **THEN** 系统 SHALL 解析文档、切分为可检索片段、建立当前已接线的轻量索引，并将片段关联到知识源版本
- **AND** 系统 SHALL NOT 将当前轻量索引描述为真实 embedding 或向量数据库索引

### Requirement: 混合检索
系统 SHALL 支持当前已接线的关键词和轻量词法评分检索，并在向 Agent 提供上下文前执行权限过滤和排序。当前 `vectorScore` SHALL 被解释为 token overlap/Jaccard 风格的轻量 lexical score；真实向量检索、embedding 相似度召回和 reranking SHALL 作为后续扩展。

#### Scenario: 检索回答上下文
- **WHEN** 用户提出知识类问题
- **THEN** 系统 SHALL 使用当前已配置的检索策略获取候选片段
- **AND** 系统 SHALL 在选择生成上下文前对候选片段排序
- **AND** 文档 SHALL 明确当前排序不依赖真实 embedding/vector DB/reranking

#### Scenario: 未来接入真实向量检索
- **WHEN** 团队决定启用 embedding-backed vector retrieval
- **THEN** 团队 SHALL 创建独立 OpenSpec 变更
- **AND** 该变更 SHALL 定义 embedding provider、向量索引、权限过滤位置、召回策略、重建流程和验收测试

### Requirement: 知识删除和版本失效
系统 SHALL 在知识源被通用知识 API 删除、被控制台撤销或被新版本替代时，使对应检索条目失效。该要求 SHALL NOT 被解释为 console 专用 hard delete REST/UI 已开放。

#### Scenario: 删除已索引文档
- **WHEN** 授权用户通过通用知识 API 删除知识源，或通过控制台撤销知识源
- **THEN** 系统 SHALL 阻止已删除或撤销的片段继续用于未来回答

#### Scenario: 控制台删除入口未开放
- **WHEN** 文档描述知识源失效能力
- **THEN** 文档 SHALL 区分通用知识删除 API、控制台 revoke 动作和未开放的 console delete REST/UI
