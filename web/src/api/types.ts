export interface LocalIdentity {
  ownerId: string;
  agentId: string;
  identityProvider: "INTERNAL" | "SSO" | "OIDC" | string;
}

export interface ApiError {
  status: number;
  message: string;
  timestamp?: string;
  accessDenied: boolean;
  details?: unknown;
}

export interface KnowledgeCitation {
  sourceId: string;
  title: string;
  version: string;
  chunkIndex: number;
  chunkId: string;
  sourceType?: "INLINE_TEXT" | "LOCAL_FILE" | "LOCAL_DIRECTORY" | "URL" | "MEMORY" | string;
  sourceUri?: string;
}

export type MessageRole = "USER" | "ASSISTANT" | "SYSTEM" | "TOOL";

export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  contentBlocks: ContentBlock[];
  createdAt: string;
  status?: "streaming" | "done" | "failed" | "cancelled";
  citations?: KnowledgeCitation[];
  noAnswerReason?: string;
}

export interface SessionSummary {
  ownerScopeId?: string;
  ownerId: string;
  agentId: string;
  sessionId: string;
  messageCount: number;
  lastMessageAt: string;
}

export interface ChatRequest {
  ownerId: string;
  agentId: string;
  sessionId: string;
  message: string;
  knowledgeEnabled: boolean;
  knowledgeLimit: number;
}

export type ContentBlockType = "TEXT" | "FILE" | "IMAGE" | "AUDIO" | "VIDEO" | "THINKING" | "TOOL_RESULT" | string;

export interface ContentBlock {
  type: ContentBlockType;
  text?: string | null;
  uri?: string | null;
  mimeType?: string | null;
  title?: string | null;
  metadata?: Record<string, unknown>;
}

export interface ChatExecutionSummary {
  status: string;
  knowledgeBacked: boolean;
  citationCount: number;
  noAnswerReason?: string | null;
  runtimeUserId: string;
  runtimeSessionId: string;
}

export interface ChatResponse {
  messageId: string;
  sessionId: string;
  message: string;
  contentBlocks: ContentBlock[];
  executionSummary: ChatExecutionSummary;
  runtimeUserId: string;
  runtimeSessionId: string;
  knowledgeBacked: boolean;
  noAnswerReason: string | null;
  citations: KnowledgeCitation[];
}

export type StreamEventKind =
  | "MODEL_STATUS"
  | "TEXT_DELTA"
  | "TOOL_EVENT"
  | "SUBAGENT_EVENT"
  | "ERROR"
  | "COMPLETION"
  | string;

export interface StreamEvent {
  type: "status" | "delta" | "tool" | "error" | "done" | string;
  kind?: StreamEventKind;
  channel?: "USER_VISIBLE" | "TOOL_EVENT" | "SYSTEM_NOTICE" | "DIAGNOSTIC" | string;
  content: string;
  terminal: boolean;
  noAnswerReason?: string;
  citations?: KnowledgeCitation[];
  metadata?: Record<string, unknown>;
}

export interface ToolStatusView {
  toolId: string;
  toolName: string;
  status: ToolExecutionStatus;
  sessionId: string;
  durationMillis: number;
}

export interface ToolConfirmationView {
  confirmationId: string;
  toolId: string;
  toolName: string;
  sessionId: string;
  riskLevel?: string;
  status?: string;
  sanitizedInput: Record<string, unknown>;
  operationSummary?: Record<string, unknown>;
  idempotencyKey?: string;
}

export interface FileUploadView {
  fileId: string;
  fileName: string;
  status: string;
}

export interface UserConsoleView {
  sessions: SessionSummary[];
  messages: ChatMessage[];
  latestCitations: KnowledgeCitation[];
  toolStatus: ToolStatusView[];
  confirmationPrompts: ToolConfirmationView[];
  fileUploads: FileUploadView[];
}

export interface AgentManagementView {
  agentId: string;
  name: string;
  systemPrompt: string;
  modelProvider: string;
  modelName: string;
  workspace: string;
  workloadType: string;
  compaction: boolean;
  maxIters: number;
}

export interface ToolDefinition {
  id: string;
  ownerScopeId?: string;
  name: string;
  description: string;
  ownerSystem: string;
  ownerId: string;
  sourceType: "INTERNAL" | "MCP" | "AGENT" | "PROTOCOL" | string;
  sourceRef: string;
  riskLevel: "READ_ONLY" | "HIGH_RISK" | string;
  mutating: boolean;
  enabled: boolean;
  parameterSchema: ToolParameterSchema;
  outputSchema: ToolOutputSchema;
  permissionPolicy: ToolPermissionPolicy;
  activityPolicy: ToolActivityPolicy;
  workloadType: string;
  createdAt: string;
  updatedAt: string;
}

export interface ToolParameterSchema {
  requiredParameters: string[];
  optionalParameters: string[];
  allowedValues: Record<string, string[]>;
  sensitiveParameters: string[];
  workspacePathParameters: string[];
}

export interface ToolOutputSchema {
  outputType: string;
  schema: Record<string, unknown>;
}

export interface ToolPermissionPolicy {
  allowedOwnerIds: string[];
  allowedAgentIds: string[];
  deniedOwnerIds: string[];
}

export interface ToolActivityPolicy {
  enabled: boolean;
  sensitiveParameters: string[];
  sensitiveResultFields: string[];
}

export type ToolExecutionStatus =
  | "SUCCEEDED"
  | "DENIED"
  | "PENDING_CONFIRMATION"
  | "DUPLICATE"
  | "IDEMPOTENCY_CONFLICT"
  | "FAILED";

export interface ToolExecutionResult {
  executionId: string;
  toolId: string;
  status: ToolExecutionStatus;
  message: string;
  output: Record<string, unknown>;
  approvalRequired: boolean;
  operationSummary: Record<string, unknown>;
}

export interface KnowledgeSourceView {
  id: string;
  agentId?: string;
  title: string;
  version: string;
  visibility: "PUBLIC" | "RESTRICTED" | string;
  allowedOwnerIds: string[];
  status: "ACTIVE" | "REVOKED" | "DELETED" | string;
  sourceType?: "INLINE_TEXT" | "LOCAL_FILE" | "LOCAL_DIRECTORY" | "URL" | "MEMORY" | string;
  sourceUri?: string;
  indexStatus: string;
  indexedAt?: string | null;
  lastSyncResult: string;
  updatedAt: string;
}

export interface KnowledgeSource {
  id: string;
  ownerScopeId?: string;
  ownerId: string;
  agentId: string;
  title: string;
  version: string;
  visibility: "PUBLIC" | "RESTRICTED" | string;
  allowedOwnerIds: string[];
  updatePolicy: string;
  sourceType: "INLINE_TEXT" | "LOCAL_FILE" | "LOCAL_DIRECTORY" | "URL" | "MEMORY" | string;
  sourceUri: string;
  indexStatus: "PENDING" | "INDEXING" | "INDEXED" | "FAILED" | "DELETED" | string;
  indexedAt?: string | null;
  status: "ACTIVE" | "REVOKED" | "DELETED" | string;
  createdAt: string;
  updatedAt: string;
}

export type MemoryLayer = "SESSION_CONTEXT" | "AGENT_MEMORY_FILE" | "FACT_LEDGER" | string;

export type MemoryWriteStatus = "PENDING_CONFIRMATION" | "CONFIRMED" | "REJECTED" | "DELETED" | string;

export interface MemoryWriteRequest {
  ownerId: string;
  agentId: string;
  sessionId: string;
  layer: MemoryLayer;
  title: string;
  content: string;
  requireConfirmation: boolean;
}

export interface PersonalMemoryRecord {
  id: string;
  ownerScopeId?: string;
  ownerId: string;
  agentId: string;
  sessionId: string;
  layer: MemoryLayer;
  title: string;
  content: string;
  status: MemoryWriteStatus;
  sourceId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeIndexMetadata {
  sourceId: string;
  agentId: string;
  sourceType: KnowledgeSource["sourceType"];
  sourceUri: string;
  version: string;
  indexStatus: KnowledgeSource["indexStatus"];
  sourceStatus: KnowledgeSource["status"];
  indexedAt?: string | null;
}

export interface PersonalDataExport {
  ownerScopeId?: string;
  ownerId: string;
  agentId: string;
  memories: PersonalMemoryRecord[];
  knowledgeSources: KnowledgeSource[];
  indexMetadata: KnowledgeIndexMetadata[];
  citationRecords: KnowledgeCitation[];
}

export interface WorkspaceFileView {
  uri: string;
  relativePath: string;
  fileName: string;
  mimeType: string;
  size: number;
  updatedAt: string;
}

export interface WorkspaceFilePreview {
  file: WorkspaceFileView;
  content: string;
  truncated: boolean;
}

export interface WorkspaceFileUpload {
  agentId: string;
  sessionId: string;
  relativePath: string;
  content: string;
  mimeType: string;
}

export interface PersonalPlanView {
  id: string;
  ownerId: string;
  agentId: string;
  sessionId: string;
  goal: string;
  steps: string[];
  uri: string;
  createdAt: string;
  status: string;
  currentStep: string;
  blockers: string[];
}

export interface SkillVersion {
  id: string;
  ownerScopeId?: string;
  skillName: string;
  version: string;
  repository: string;
  ownerId: string;
  status: "PROPOSED" | "APPROVED" | "PUBLISHED" | "DISABLED" | "ROLLED_BACK" | string;
  approvedBy: string;
  updatedAt: string;
}

export interface SkillPermissionSet {
  fileRead: boolean;
  fileWrite: boolean;
  toolExecution: boolean;
  networkAccess: boolean;
  memoryWrite: boolean;
  sandbox: boolean;
}

export interface PersonalSkill {
  id: string;
  ownerScopeId?: string;
  ownerId: string;
  name: string;
  description: string;
  version: string;
  triggers: string[];
  sourceType: string;
  source: string;
  permissions: SkillPermissionSet;
  resources: string[];
  agentIds: string[];
  status: "ENABLED" | "DISABLED" | "LOCKED" | "ROLLED_BACK" | string;
  updatedAt: string;
}

export interface SkillValidationResult {
  skillName: string;
  version: string;
  source: string;
  valid: boolean;
  errors: string[];
}

export interface ToolPendingConfirmation {
  confirmationId: string;
  ownerScopeId?: string;
  ownerId: string;
  agentId: string;
  sessionId: string;
  toolId: string;
  toolName: string;
  sourceType: string;
  riskLevel: string;
  status: string;
  parameters: Record<string, unknown>;
  sanitizedInput: Record<string, unknown>;
  operationSummary: Record<string, unknown>;
  idempotencyKey: string;
  createdAt: string;
  updatedAt: string;
  expiresAt: string;
  decidedAt?: string | null;
  decisionReason: string;
}

export interface OperationalMetricSummary {
  sessionCount: number;
  modelOrAgentEvents: number;
  toolCalls: number;
  ragHits: number;
  ragMisses: number;
  failures: number;
  totalDurationMillis: number;
  feedbackCount: number;
}

export interface CostUsageReport {
  ownerScopeId?: string;
  agentId: string;
  providerId: string;
  tokenEvents: number;
  estimatedTokens: number;
  estimatedCost: number;
}

export interface ToolActivityRecord {
  id: string;
  occurredAt: string;
  ownerScopeId?: string;
  ownerId: string;
  agentId: string;
  sessionId: string;
  toolId: string;
  toolName: string;
  sourceType: string;
  riskLevel: string;
  status: ToolExecutionStatus;
  sanitizedInput: Record<string, unknown>;
  sanitizedOutput: Record<string, unknown>;
  durationMillis: number;
  idempotencyKey: string;
  failureReason: string;
}

export interface SecurityActivityRecord {
  id: string;
  occurredAt: string;
  ownerScopeId?: string;
  ownerId: string;
  resourceType: string;
  resourceId: string;
  action: string;
  sanitizedDetails: Record<string, unknown>;
}

export interface ConsoleActivityResult {
  toolActivity: ToolActivityRecord[];
  securityActivity: SecurityActivityRecord[];
}

export interface OrchestrationStep {
  id: string;
  agentId: string;
  action: string;
  input: Record<string, unknown>;
  output: Record<string, unknown>;
  status: string;
}

export interface HandoffRecord {
  occurredAt: string;
  fromAgentId: string;
  toAgentId: string;
  reason: string;
  sharedContext: Record<string, unknown>;
}

export interface OrchestrationTrace {
  id: string;
  occurredAt: string;
  ownerScopeId?: string;
  ownerId: string;
  supervisorAgentId: string;
  selectedAgentId: string;
  taskIntent: string;
  confidence: number;
  status: string;
  candidateAgentIds: string[];
  steps: OrchestrationStep[];
  handoffs: HandoffRecord[];
  attributes: Record<string, unknown>;
}
