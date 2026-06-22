import { identityHeaders, identityPayload, identitySearchParams } from "./identity";
import type {
  AgentManagementView,
  ApiError,
  ChatMessage,
  ChatResponse,
  ConsoleAuditResult,
  CostUsageReport,
  EndToEndAcceptanceReport,
  KnowledgeSourceView,
  LocalIdentity,
  OperationalMetricSummary,
  OrchestrationTrace,
  PhaseGate,
  ReleaseScenario,
  RollbackAction,
  SessionSummary,
  SkillVersion,
  StreamEvent,
  ToolConfirmationView,
  ToolDefinition,
  ToolExecutionResult,
  UserConsoleView
} from "./types";

export type Fetcher = typeof fetch;

export interface ApiClientOptions {
  fetcher?: Fetcher;
  getIdentity: () => LocalIdentity;
}

export async function parseApiError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get("Content-Type") ?? "";
  let message = response.statusText || `Request failed with status ${response.status}`;
  let timestamp: string | undefined;
  let details: unknown;

  try {
    if (contentType.includes("application/json")) {
      const body = (await response.json()) as { message?: string; timestamp?: string };
      details = body;
      message = body.message || message;
      timestamp = body.timestamp;
    } else {
      const text = await response.text();
      message = text || message;
      details = text;
    }
  } catch {
    details = undefined;
  }

  const normalized = message.toLowerCase();
  const accessDenied =
    response.status === 401 ||
    response.status === 403 ||
    normalized.includes("role is required") ||
    normalized.includes("authenticated identity") ||
    normalized.includes("authenticated tenant") ||
    normalized.includes("authenticated user") ||
    normalized.includes("authenticated agent") ||
    normalized.includes("permission") ||
    normalized.includes("not authorized") ||
    normalized.includes("access denied");

  return {
    status: response.status,
    message,
    timestamp,
    accessDenied,
    details
  };
}

export class ApiClientError extends Error {
  readonly apiError: ApiError;

  constructor(apiError: ApiError) {
    super(apiError.message);
    this.name = "ApiClientError";
    this.apiError = apiError;
  }
}

export function createSseParser(onEvent: (event: StreamEvent) => void) {
  let buffer = "";

  function consume(frame: string) {
    const dataLines: string[] = [];
    let eventName = "";
    for (const rawLine of frame.split(/\r?\n/)) {
      const line = rawLine.trimEnd();
      if (line.startsWith("event:")) {
        eventName = line.slice("event:".length).trim();
      }
      if (line.startsWith("data:")) {
        dataLines.push(line.slice("data:".length).trimStart());
      }
    }
    if (dataLines.length === 0) {
      return;
    }
    const rawData = dataLines.join("\n");
    try {
      const parsed = JSON.parse(rawData) as Partial<StreamEvent>;
      onEvent({
        type: parsed.type || eventName || "message",
        kind: parsed.kind,
        content: parsed.content || "",
        terminal: Boolean(parsed.terminal),
        noAnswerReason: parsed.noAnswerReason,
        citations: parsed.citations,
        metadata: parsed.metadata
      });
    } catch {
      onEvent({
        type: eventName || "message",
        content: rawData,
        terminal: false
      });
    }
  }

  return {
    feed(chunk: string) {
      buffer += chunk;
      const frames = buffer.split(/\r?\n\r?\n/);
      buffer = frames.pop() ?? "";
      frames.forEach(consume);
    },
    flush() {
      const frame = buffer.trim();
      buffer = "";
      if (frame) {
        consume(frame);
      }
    }
  };
}

export class ApiClient {
  private readonly fetcher: Fetcher;
  private readonly getIdentity: () => LocalIdentity;

  constructor(options: ApiClientOptions) {
    this.fetcher = options.fetcher ?? globalThis.fetch.bind(globalThis);
    this.getIdentity = options.getIdentity;
  }

  listSessions(agentId: string) {
    const identity = this.getIdentity();
    const params = identitySearchParams(identity);
    params.set("agentId", agentId);
    return this.get<SessionSummary[]>(`/api/sessions?${params}`);
  }

  listMessages(agentId: string, sessionId: string) {
    const identity = this.getIdentity();
    const params = identitySearchParams(identity);
    params.set("agentId", agentId);
    params.set("sessionId", sessionId);
    return this.get<ChatMessage[]>(`/api/messages?${params}`);
  }

  deleteSession(agentId: string, sessionId: string) {
    const identity = this.getIdentity();
    const params = identitySearchParams(identity);
    params.set("agentId", agentId);
    return this.request<{ deleted: boolean }>(`/api/sessions/${encodeURIComponent(sessionId)}?${params}`, {
      method: "DELETE"
    });
  }

  chat(input: {
    agentId: string;
    sessionId: string;
    message: string;
    knowledgeEnabled: boolean;
    knowledgeLimit: number;
  }) {
    return this.post<ChatResponse>("/api/chat", {
      ...identityPayload(this.getIdentity()),
      ...input
    });
  }

  async streamChat(
    input: {
      agentId: string;
      sessionId: string;
      message: string;
      knowledgeEnabled: boolean;
      knowledgeLimit: number;
    },
    onEvent: (event: StreamEvent) => void,
    signal?: AbortSignal
  ) {
    const response = await this.fetcher("/api/chat/stream", {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify({
        ...identityPayload(this.getIdentity()),
        ...input
      }),
      signal
    });
    if (!response.ok) {
      throw new ApiClientError(await parseApiError(response));
    }
    if (!response.body) {
      throw new ApiClientError({
        status: response.status,
        message: "Streaming response did not include a readable body.",
        accessDenied: false
      });
    }
    const parser = createSseParser(onEvent);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      parser.feed(decoder.decode(value, { stream: true }));
    }
    parser.feed(decoder.decode());
    parser.flush();
  }

  getUserConsole(agentId: string, sessionId?: string) {
    const identity = this.getIdentity();
    const params = identitySearchParams(identity);
    params.set("agentId", agentId);
    if (sessionId) {
      params.set("sessionId", sessionId);
    }
    return this.get<UserConsoleView>(`/api/console/user?${params}`);
  }

  listAgents() {
    return this.get<AgentManagementView[]>(`/api/console/agents?${identitySearchParams(this.getIdentity())}`);
  }

  updateAgentPrompt(agentId: string, systemPrompt: string) {
    return this.patch<AgentManagementView>(
      `/api/console/agents/${encodeURIComponent(agentId)}/prompt?${identitySearchParams(this.getIdentity())}`,
      { systemPrompt }
    );
  }

  updateAgentConfig(
    agentId: string,
    payload: Partial<Pick<AgentManagementView, "modelProvider" | "modelName" | "workspace" | "compaction" | "maxIters">>
  ) {
    return this.patch<AgentManagementView>(
      `/api/console/agents/${encodeURIComponent(agentId)}/config?${identitySearchParams(this.getIdentity())}`,
      payload
    );
  }

  listConsoleTools() {
    return this.get<ToolDefinition[]>(`/api/console/tools?${identitySearchParams(this.getIdentity())}`);
  }

  setToolEnabled(toolId: string, enabled: boolean) {
    return this.patch<ToolDefinition>(
      `/api/console/tools/${encodeURIComponent(toolId)}/enabled?${identitySearchParams(this.getIdentity())}`,
      { enabled }
    );
  }

  executeToolConfirmation(
    agentId: string,
    confirmation: ToolConfirmationView,
    confirmed: boolean
  ): Promise<ToolExecutionResult> {
    return this.post<ToolExecutionResult>("/api/tools/execute", {
      ...identityPayload(this.getIdentity()),
      agentId,
      sessionId: confirmation.sessionId,
      toolId: confirmation.toolId,
      parameters: confirmation.sanitizedInput,
      confirmed,
      approvalId: confirmed ? stableToolActionId("confirm", confirmation) : "",
      reviewerId: confirmed ? this.getIdentity().userId : "",
      idempotencyKey: stableIdempotencyKey(confirmation)
    });
  }

  rejectToolConfirmation(agentId: string, confirmation: ToolConfirmationView): Promise<ToolExecutionResult> {
    return this.post<ToolExecutionResult>("/api/tools/reject", {
      ...identityPayload(this.getIdentity()),
      agentId,
      sessionId: confirmation.sessionId,
      toolId: confirmation.toolId,
      parameters: confirmation.sanitizedInput,
      confirmed: false,
      approvalId: stableToolActionId("reject", confirmation),
      reviewerId: this.getIdentity().userId,
      idempotencyKey: stableIdempotencyKey(confirmation)
    });
  }

  listKnowledge() {
    return this.get<KnowledgeSourceView[]>(`/api/console/knowledge?${identitySearchParams(this.getIdentity())}`);
  }

  revokeKnowledge(sourceId: string) {
    return this.patch<KnowledgeSourceView>(
      `/api/console/knowledge/${encodeURIComponent(sourceId)}/revoke?${identitySearchParams(this.getIdentity())}`,
      undefined
    );
  }

  listSkills(skillName?: string) {
    const params = identitySearchParams(this.getIdentity());
    if (skillName) {
      params.set("skillName", skillName);
    }
    return this.get<SkillVersion[]>(`/api/console/skills?${params}`);
  }

  approveSkill(versionId: string) {
    return this.patch<SkillVersion>(
      `/api/console/skills/${encodeURIComponent(versionId)}/approve?${identitySearchParams(this.getIdentity())}`,
      undefined
    );
  }

  publishSkill(versionId: string) {
    return this.patch<SkillVersion>(
      `/api/console/skills/${encodeURIComponent(versionId)}/publish?${identitySearchParams(this.getIdentity())}`,
      undefined
    );
  }

  disableSkill(versionId: string) {
    return this.patch<SkillVersion>(
      `/api/console/skills/${encodeURIComponent(versionId)}/disable?${identitySearchParams(this.getIdentity())}`,
      undefined
    );
  }

  metrics() {
    return this.get<OperationalMetricSummary>(`/api/console/metrics?${identitySearchParams(this.getIdentity())}`);
  }

  cost(agentId?: string, providerId?: string) {
    const params = identitySearchParams(this.getIdentity());
    if (agentId) {
      params.set("agentId", agentId);
    }
    if (providerId) {
      params.set("providerId", providerId);
    }
    return this.get<CostUsageReport>(`/api/console/cost?${params}`);
  }

  audit(filters: {
    targetUserId?: string;
    sessionId?: string;
    resourceId?: string;
    action?: string;
    from?: string;
    to?: string;
  }) {
    const params = identitySearchParams(this.getIdentity());
    for (const [key, value] of Object.entries(filters)) {
      if (value) {
        params.set(key, key === "from" || key === "to" ? toInstant(value) : value);
      }
    }
    return this.get<ConsoleAuditResult>(`/api/console/audit?${params}`);
  }

  releaseScenario() {
    return this.get<ReleaseScenario>("/api/release/scenario");
  }

  phaseGates() {
    return this.get<PhaseGate[]>("/api/release/phase-gates");
  }

  rollbackActions() {
    return this.get<RollbackAction[]>("/api/release/rollback");
  }

  acceptance() {
    return this.get<EndToEndAcceptanceReport>("/api/release/acceptance");
  }

  orchestrationTraces() {
    return this.get<OrchestrationTrace[]>(`/api/orchestration/traces?${identitySearchParams(this.getIdentity())}`);
  }

  private get<T>(path: string) {
    return this.request<T>(path);
  }

  private post<T>(path: string, body: unknown) {
    return this.request<T>(path, {
      method: "POST",
      body: JSON.stringify(body)
    });
  }

  private patch<T>(path: string, body: unknown) {
    return this.request<T>(path, {
      method: "PATCH",
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await this.fetcher(path, {
      ...init,
      headers: this.headers(init.headers)
    });
    if (!response.ok) {
      throw new ApiClientError(await parseApiError(response));
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  private headers(headers?: HeadersInit): Headers {
    const result = new Headers(headers);
    result.set("Accept", "application/json");
    if (!result.has("Content-Type")) {
      result.set("Content-Type", "application/json");
    }
    for (const [key, value] of Object.entries(identityHeaders(this.getIdentity()))) {
      result.set(key, value);
    }
    return result;
  }
}

function stableIdempotencyKey(confirmation: ToolConfirmationView): string {
  return confirmation.idempotencyKey || stableToolActionId("idem", confirmation);
}

function stableToolActionId(prefix: string, confirmation: ToolConfirmationView): string {
  const raw = `${confirmation.toolId}:${confirmation.sessionId}:${confirmation.idempotencyKey || ""}:${stableJson(
    confirmation.sanitizedInput
  )}`;
  return `ui-${prefix}-${hash(raw)}`;
}

function stableJson(value: unknown): string {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return JSON.stringify(
      Object.fromEntries(
        Object.entries(value as Record<string, unknown>)
          .sort(([first], [second]) => first.localeCompare(second))
          .map(([key, item]) => [key, stableJsonValue(item)])
      )
    );
  }
  return JSON.stringify(value);
}

function stableJsonValue(value: unknown): unknown {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .sort(([first], [second]) => first.localeCompare(second))
        .map(([key, item]) => [key, stableJsonValue(item)])
    );
  }
  if (Array.isArray(value)) {
    return value.map(stableJsonValue);
  }
  return value;
}

function hash(value: string): string {
  let result = 0;
  for (let index = 0; index < value.length; index += 1) {
    result = (result * 31 + value.charCodeAt(index)) >>> 0;
  }
  return result.toString(36);
}

function toInstant(value: string): string {
  if (/[zZ]|[+-]\d{2}:\d{2}$/.test(value)) {
    return new Date(value).toISOString();
  }
  return new Date(value).toISOString();
}
