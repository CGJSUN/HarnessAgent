export type Role = "employee" | "admin" | "ops" | "auditor" | "knowledge" | "tool" | string;

export interface LocalIdentity {
  tenantId: string;
  userId: string;
  roles: Role[];
  departments: string[];
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
}

export type MessageRole = "USER" | "ASSISTANT" | "SYSTEM" | "TOOL";

export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: string;
  status?: "streaming" | "done" | "failed" | "cancelled";
  citations?: KnowledgeCitation[];
  noAnswerReason?: string;
}

export interface SessionSummary {
  tenantId: string;
  userId: string;
  agentId: string;
  sessionId: string;
  messageCount: number;
  lastMessageAt: string;
}

export interface ChatRequest {
  tenantId: string;
  userId: string;
  agentId: string;
  sessionId: string;
  message: string;
  knowledgeEnabled: boolean;
  departments: string[];
  roles: string[];
  knowledgeLimit: number;
}

export interface ChatResponse {
  message: string;
  runtimeUserId: string;
  runtimeSessionId: string;
  knowledgeBacked: boolean;
  noAnswerReason: string | null;
  citations: KnowledgeCitation[];
}

export interface StreamEvent {
  type: "status" | "delta" | "tool" | "error" | "done" | string;
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
  toolId: string;
  toolName: string;
  sessionId: string;
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
  tenantId: string;
  name: string;
  description: string;
  ownerSystem: string;
  ownerId: string;
  sourceType: "INTERNAL" | "MCP" | "AGENT" | string;
  sourceRef: string;
  riskLevel: "READ_ONLY" | "HIGH_RISK" | string;
  mutating: boolean;
  enabled: boolean;
  parameterSchema: ToolParameterSchema;
  permissionPolicy: ToolPermissionPolicy;
  auditPolicy: ToolAuditPolicy;
  createdAt: string;
  updatedAt: string;
}

export interface ToolParameterSchema {
  requiredParameters: string[];
  optionalParameters: string[];
  allowedValues: Record<string, string[]>;
  sensitiveParameters: string[];
}

export interface ToolPermissionPolicy {
  allowedTenantIds: string[];
  allowedUserIds: string[];
  allowedAgentIds: string[];
  allowedDepartments: string[];
  allowedRoles: string[];
}

export interface ToolAuditPolicy {
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
  title: string;
  version: string;
  visibility: "PUBLIC" | "RESTRICTED" | string;
  allowedDepartments: string[];
  allowedRoles: string[];
  allowedUsers: string[];
  status: "ACTIVE" | "REVOKED" | "DELETED" | string;
  indexStatus: string;
  lastSyncResult: string;
  updatedAt: string;
}

export interface KnowledgeSource {
  id: string;
  tenantId: string;
  ownerId: string;
  title: string;
  version: string;
  visibility: "PUBLIC" | "RESTRICTED" | string;
  allowedDepartments: string[];
  allowedRoles: string[];
  allowedUsers: string[];
  updatePolicy: string;
  status: "ACTIVE" | "REVOKED" | "DELETED" | string;
  createdAt: string;
  updatedAt: string;
}

export interface SkillVersion {
  id: string;
  tenantId: string;
  skillName: string;
  version: string;
  repository: string;
  ownerId: string;
  status: "PROPOSED" | "APPROVED" | "PUBLISHED" | "DISABLED" | "ROLLED_BACK" | string;
  approvedBy: string;
  updatedAt: string;
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
  tenantId: string;
  agentId: string;
  providerId: string;
  tokenEvents: number;
  estimatedTokens: number;
  estimatedCost: number;
}

export interface ToolAuditRecord {
  id: string;
  occurredAt: string;
  tenantId: string;
  userId: string;
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
  approvalId: string;
  reviewerId: string;
  idempotencyKey: string;
  failureReason: string;
}

export interface SecurityAuditRecord {
  id: string;
  occurredAt: string;
  tenantId: string;
  userId: string;
  resourceType: string;
  resourceId: string;
  action: string;
  sanitizedDetails: Record<string, unknown>;
}

export interface ConsoleAuditResult {
  toolAudit: ToolAuditRecord[];
  securityAudit: SecurityAuditRecord[];
}

export interface ReleaseScenario {
  scenario: string;
  acceptanceCriteria: string[];
}

export interface PhaseGate {
  name: string;
  status: "PASSED" | "BLOCKED" | string;
  checks: string[];
  rollbackSwitch: string;
}

export interface RollbackAction {
  capability: string;
  action: string;
  auditRequirement: string;
}

export interface EndToEndAcceptanceReport {
  tenantIsolation: boolean;
  permissionFiltering: boolean;
  highRiskConfirmation: boolean;
  auditTraceability: boolean;
  operationalObservability: boolean;
  notes: string[];
  passed?: boolean;
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
  tenantId: string;
  userId: string;
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
