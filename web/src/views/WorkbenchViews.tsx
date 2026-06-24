import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  CheckCircle2,
  Download,
  Lock,
  Play,
  Plus,
  RefreshCcw,
  RotateCcw,
  Save,
  Search,
  ToggleLeft,
  ToggleRight,
  Trash2,
  Upload
} from "lucide-react";
import { ApiClient, ApiClientError } from "../api/client";
import type {
  AgentManagementView,
  CostUsageReport,
  KnowledgeSource,
  OperationalMetricSummary,
  OrchestrationTrace,
  PersonalMemoryRecord,
  PersonalPlanView,
  PersonalSkill,
  ToolActivityRecord,
  ToolDefinition,
  WorkspaceFilePreview,
  WorkspaceFileView
} from "../api/types";
import {
  AccessDenied,
  EmptyState,
  ErrorState,
  IconButton,
  JsonPreview,
  LoadingState,
  SectionHeader,
  StatusBadge,
  TextAreaField,
  TextField,
  ToggleField
} from "../components/common";
import { formatCost, formatDateTime, formatNumber } from "../lib/format";

const DEFAULT_AGENT_ID = "personal-assistant";
const DEFAULT_SESSION_ID = "session-workbench";

export function TasksWorkspace({ api }: { api: ApiClient }) {
  const [agentId, setAgentId] = useState(DEFAULT_AGENT_ID);
  const [sessionId, setSessionId] = useState(DEFAULT_SESSION_ID);
  const [plans, setPlans] = useState<PersonalPlanView[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [goal, setGoal] = useState("Review and complete the current personal agent task");
  const [stepsText, setStepsText] = useState("Read current workspace context\nApply the next focused change\nVerify behavior and record blockers");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const selectedPlan = useMemo(
    () => plans.find(plan => plan.id === selectedPlanId) ?? plans[0],
    [plans, selectedPlanId]
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      const next = await api.listPlans(agentId, sessionId);
      setPlans(next);
      setSelectedPlanId(previous => previous || next[0]?.id || "");
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }, [agentId, api, sessionId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function createPlan(event: FormEvent) {
    event.preventDefault();
    const steps = stepsText
      .split(/\r?\n/)
      .map(step => step.trim())
      .filter(Boolean);
    if (!goal.trim() || steps.length === 0) {
      setError("Plan goal and at least one step are required.");
      return;
    }
    try {
      const created = await api.createPlan(agentId, sessionId, { goal, steps });
      setPlans(previous => [created, ...previous.filter(plan => plan.id !== created.id)]);
      setSelectedPlanId(created.id);
      setError(null);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  return (
    <section className="main-panel">
      <SectionHeader
        title="Tasks and plans"
        eyebrow="workspace://plans"
        actions={
          <IconButton title="Refresh plans" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {loading ? <LoadingState /> : null}
      <form className="filter-bar" onSubmit={createPlan}>
        <TextField label="Agent" value={agentId} onChange={setAgentId} />
        <TextField label="Session" value={sessionId} onChange={setSessionId} />
        <TextField label="Goal" value={goal} onChange={setGoal} />
        <label className="field filter-span-2">
          <span>Steps</span>
          <textarea rows={3} value={stepsText} onChange={event => setStepsText(event.target.value)} />
        </label>
        <button className="command-button command-button-primary" type="submit">
          <Plus size={16} aria-hidden="true" />
          Create plan
        </button>
      </form>
      <div className="detail-layout">
        <div className="list-panel">
          {plans.length === 0 ? <EmptyState title="No plans" detail="Create or request a plan to persist it under the personal workspace." /> : null}
          {plans.map(plan => (
            <button
              className={`list-row ${selectedPlan?.id === plan.id ? "is-active" : ""}`}
              key={plan.id}
              type="button"
              onClick={() => setSelectedPlanId(plan.id)}
            >
              <strong>{plan.goal}</strong>
              <span>{plan.uri}</span>
              <StatusBadge value={plan.status} />
            </button>
          ))}
        </div>
        <div className="edit-panel">
          {selectedPlan ? (
            <>
              <div className="summary-band">
                <div>
                  <span>Plan file</span>
                  <strong>{selectedPlan.uri}</strong>
                </div>
                <div>
                  <span>Current step</span>
                  <strong>{selectedPlan.currentStep || "No active step"}</strong>
                </div>
                <div>
                  <span>Blockers</span>
                  <strong>{selectedPlan.blockers.length}</strong>
                </div>
              </div>
              <ol className="plan-steps">
                {selectedPlan.steps.map((step, index) => (
                  <li key={`${selectedPlan.id}-${step}`}>
                    <CheckCircle2 size={16} aria-hidden="true" />
                    <span>{step}</span>
                    <StatusBadge value={index === 0 ? "CURRENT" : "PENDING"} />
                  </li>
                ))}
              </ol>
            </>
          ) : (
            <EmptyState title="No selected plan" />
          )}
        </div>
      </div>
    </section>
  );
}

export function FilesWorkspace({
  api,
  initialReference
}: {
  api: ApiClient;
  initialReference?: string;
}) {
  const [agentId, setAgentId] = useState(DEFAULT_AGENT_ID);
  const [sessionId, setSessionId] = useState(DEFAULT_SESSION_ID);
  const [files, setFiles] = useState<WorkspaceFileView[]>([]);
  const [selectedPath, setSelectedPath] = useState(initialReference || "");
  const [preview, setPreview] = useState<WorkspaceFilePreview | null>(null);
  const [relativePath, setRelativePath] = useState("artifacts/note.md");
  const [mimeType, setMimeType] = useState("text/markdown");
  const [content, setContent] = useState("# Note\n");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      const next = await api.listWorkspaceFiles(agentId, sessionId);
      setFiles(next);
      setSelectedPath(previous => previous || initialReference || next[0]?.uri || "");
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }, [agentId, api, initialReference, sessionId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (initialReference) {
      setSelectedPath(initialReference);
    }
  }, [initialReference]);

  useEffect(() => {
    if (!selectedPath) {
      setPreview(null);
      return;
    }
    api.previewWorkspaceFile(agentId, selectedPath, sessionId)
      .then(setPreview)
      .catch(caught => handleApiError(caught, setError, setAccessDenied));
  }, [agentId, api, selectedPath, sessionId]);

  async function upload(event: FormEvent) {
    event.preventDefault();
    try {
      const uploaded = await api.uploadWorkspaceFile({
        agentId,
        sessionId,
        relativePath,
        content,
        mimeType
      });
      setFiles(previous => [uploaded, ...previous.filter(file => file.uri !== uploaded.uri)]);
      setSelectedPath(uploaded.uri);
      setError(null);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  async function readUploadFile(filesToRead: FileList | null) {
    const file = filesToRead?.[0];
    if (!file) {
      return;
    }
    setRelativePath(`artifacts/${safeFileName(file.name)}`);
    setMimeType(file.type || "application/octet-stream");
    setContent(await file.text());
  }

  async function downloadSelected() {
    if (!preview) {
      return;
    }
    try {
      const blob = await api.downloadWorkspaceFile(agentId, preview.file.uri, sessionId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = preview.file.fileName;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  async function deleteSelected() {
    if (!preview) {
      return;
    }
    try {
      await api.deleteWorkspaceFile(agentId, preview.file.uri, sessionId);
      setFiles(previous => previous.filter(file => file.uri !== preview.file.uri));
      setSelectedPath("");
      setPreview(null);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  return (
    <section className="main-panel">
      <SectionHeader
        title="Workspace files"
        eyebrow={selectedPath ? `Reference ${selectedPath}` : "workspace://"}
        actions={
          <IconButton title="Refresh files" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {loading ? <LoadingState /> : null}
      <form className="filter-bar" onSubmit={upload}>
        <TextField label="Agent" value={agentId} onChange={setAgentId} />
        <TextField label="Session" value={sessionId} onChange={setSessionId} />
        <TextField label="Upload path" value={relativePath} onChange={setRelativePath} />
        <TextField label="MIME type" value={mimeType} onChange={setMimeType} />
        <label className="command-button">
          <Upload size={16} aria-hidden="true" />
          Pick file
          <input className="visually-hidden" type="file" onChange={event => void readUploadFile(event.target.files)} />
        </label>
        <button className="command-button command-button-primary" type="submit">
          <Upload size={16} aria-hidden="true" />
          Upload
        </button>
        <label className="field filter-span-3">
          <span>Upload content</span>
          <textarea rows={4} value={content} onChange={event => setContent(event.target.value)} />
        </label>
      </form>
      <div className="detail-layout">
        <div className="list-panel">
          {files.length === 0 ? <EmptyState title="No files" /> : null}
          {files.map(file => (
            <button
              className={`list-row ${selectedPath === file.uri ? "is-active" : ""}`}
              key={file.uri}
              type="button"
              onClick={() => setSelectedPath(file.uri)}
            >
              <strong>{file.fileName}</strong>
              <span>{file.relativePath}</span>
              <span>{formatNumber(file.size)} bytes · {formatDateTime(file.updatedAt)}</span>
            </button>
          ))}
        </div>
        <div className="edit-panel">
          {preview ? (
            <>
              <div className="summary-band">
                <div>
                  <span>URI</span>
                  <strong>{preview.file.uri}</strong>
                </div>
                <div>
                  <span>Type</span>
                  <strong>{preview.file.mimeType}</strong>
                </div>
                <div>
                  <span>Preview</span>
                  <strong>{preview.truncated ? "Truncated" : "Complete"}</strong>
                </div>
              </div>
              <div className="button-row">
                <button className="command-button" type="button" onClick={() => void downloadSelected()}>
                  <Download size={16} aria-hidden="true" />
                  Download
                </button>
                <button className="command-button" type="button" onClick={() => void deleteSelected()}>
                  <Trash2 size={16} aria-hidden="true" />
                  Delete
                </button>
              </div>
              <pre className="file-preview">{preview.content}</pre>
            </>
          ) : (
            <EmptyState title="No file selected" detail="Open a file from the list or from a chat citation." />
          )}
        </div>
      </div>
    </section>
  );
}

export function KnowledgeWorkspace({ api }: { api: ApiClient }) {
  const [agentId, setAgentId] = useState(DEFAULT_AGENT_ID);
  const [sources, setSources] = useState<KnowledgeSource[]>([]);
  const [memories, setMemories] = useState<PersonalMemoryRecord[]>([]);
  const [memoryTitle, setMemoryTitle] = useState("Preference");
  const [memoryContent, setMemoryContent] = useState("Use concise implementation notes.");
  const [layer, setLayer] = useState("FACT_LEDGER");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      const [nextSources, nextMemories] = await Promise.all([
        api.listKnowledgeSources(agentId),
        api.listMemories(agentId)
      ]);
      setSources(nextSources.filter(source => !source.agentId || source.agentId === agentId));
      setMemories(nextMemories);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }, [agentId, api]);

  useEffect(() => {
    void load();
  }, [load]);

  async function addMemory(event: FormEvent) {
    event.preventDefault();
    try {
      const memory = await api.requestMemoryWrite({
        agentId,
        sessionId: DEFAULT_SESSION_ID,
        layer,
        title: memoryTitle,
        content: memoryContent,
        requireConfirmation: false
      });
      setMemories(previous => [memory, ...previous.filter(item => item.id !== memory.id)]);
      setNotice("Memory saved");
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  async function deleteMemory(memory: PersonalMemoryRecord) {
    try {
      await api.deleteMemory(agentId, memory.id);
      setMemories(previous => previous.filter(item => item.id !== memory.id));
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  async function deleteSource(source: KnowledgeSource) {
    try {
      await api.deleteKnowledgeSource(source.id);
      setSources(previous => previous.filter(item => item.id !== source.id));
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  async function exportData() {
    try {
      const exported = await api.exportPersonalData(agentId);
      setNotice(`Export ready: ${exported.memories.length} memories and ${exported.knowledgeSources.length} sources`);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  return (
    <section className="main-panel">
      <SectionHeader
        title="Knowledge and memory"
        actions={
          <IconButton title="Refresh knowledge" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {notice ? <div className="inline-notice">{notice}</div> : null}
      {loading ? <LoadingState /> : null}
      <form className="filter-bar" onSubmit={addMemory}>
        <TextField label="Agent" value={agentId} onChange={setAgentId} />
        <TextField label="Memory layer" value={layer} onChange={setLayer} />
        <TextField label="Memory title" value={memoryTitle} onChange={setMemoryTitle} />
        <label className="field filter-span-2">
          <span>Memory content</span>
          <textarea rows={3} value={memoryContent} onChange={event => setMemoryContent(event.target.value)} />
        </label>
        <button className="command-button command-button-primary" type="submit">
          <Plus size={16} aria-hidden="true" />
          Add memory
        </button>
        <button className="command-button" type="button" onClick={() => void exportData()}>
          <Download size={16} aria-hidden="true" />
          Export
        </button>
        <button className="command-button" type="button" onClick={() => { setNotice("Rebuild requested from current source metadata"); void load(); }}>
          <RotateCcw size={16} aria-hidden="true" />
          Rebuild index
        </button>
      </form>
      <div className="split-grid">
        <section className="stack">
          <h3>Knowledge sources</h3>
          {sources.length === 0 ? <EmptyState title="No knowledge sources" /> : null}
          {sources.map(source => (
            <article className="list-item" key={source.id}>
              <strong>{source.title}</strong>
              <span>{source.sourceType} · {source.sourceUri}</span>
              <StatusBadge value={source.indexStatus || source.status} />
              <div className="button-row">
                <button className="command-button" type="button" onClick={() => void deleteSource(source)}>
                  <Trash2 size={16} aria-hidden="true" />
                  Delete
                </button>
              </div>
            </article>
          ))}
        </section>
        <section className="stack">
          <h3>Personal memories</h3>
          {memories.length === 0 ? <EmptyState title="No memories" /> : null}
          {memories.map(memory => (
            <article className="list-item" key={memory.id}>
              <strong>{memory.title}</strong>
              <span>{memory.layer} · {formatDateTime(memory.updatedAt)}</span>
              <p>{memory.content}</p>
              <StatusBadge value={memory.status} />
              <div className="button-row">
                <button className="command-button" type="button" onClick={() => void deleteMemory(memory)}>
                  <Trash2 size={16} aria-hidden="true" />
                  Delete
                </button>
              </div>
            </article>
          ))}
        </section>
      </div>
    </section>
  );
}

export function ToolsSkillsWorkspace({ api }: { api: ApiClient }) {
  const [agentId, setAgentId] = useState(DEFAULT_AGENT_ID);
  const [sessionId, setSessionId] = useState(DEFAULT_SESSION_ID);
  const [tools, setTools] = useState<ToolDefinition[]>([]);
  const [skills, setSkills] = useState<PersonalSkill[]>([]);
  const [validation, setValidation] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      const [nextTools, nextSkills] = await Promise.all([
        api.listTools(),
        api.listPersonalSkills()
      ]);
      setTools(nextTools);
      setSkills(nextSkills.filter(skill => skill.agentIds.length === 0 || skill.agentIds.includes(agentId)));
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }, [agentId, api]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggleTool(tool: ToolDefinition) {
    try {
      const next = await api.setToolEnabled(tool.id, !tool.enabled);
      setTools(previous => previous.map(item => (item.id === next.id ? next : item)));
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  async function skillAction(skill: PersonalSkill, action: "enable" | "disable" | "upgrade" | "rollback" | "lock") {
    try {
      const next = await api.setPersonalSkillStatus(skill, action);
      setSkills(previous => previous.map(item => (item.id === next.id ? next : item)));
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  async function validateSkill(skill: PersonalSkill) {
    try {
      const result = await api.validateLocalSkill(agentId, skill.source || skill.name);
      setValidation(result.valid ? `${result.skillName} ${result.version} passed validation` : result.errors.join(", "));
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  return (
    <section className="main-panel">
      <SectionHeader
        title="Tools and skills"
        actions={
          <IconButton title="Refresh tools and skills" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {validation ? <div className="inline-notice">{validation}</div> : null}
      {loading ? <LoadingState /> : null}
      <div className="filter-bar">
        <TextField label="Agent" value={agentId} onChange={setAgentId} />
        <TextField label="Session" value={sessionId} onChange={setSessionId} />
        <button className="command-button" type="button" onClick={() => void api.listPendingToolConfirmations(agentId, sessionId)}>
          <Search size={16} aria-hidden="true" />
          Check HITL
        </button>
      </div>
      <div className="split-grid">
        <section className="stack">
          <h3>Tools</h3>
          {tools.length === 0 ? <EmptyState title="No tools" /> : null}
          {tools.map(tool => (
            <article className="list-item" key={tool.id}>
              <strong>{tool.name}</strong>
              <span>{tool.description}</span>
              <div className="button-row">
                <StatusBadge value={tool.riskLevel} />
                <StatusBadge value={tool.enabled} />
                <button className="command-button" type="button" onClick={() => void toggleTool(tool)}>
                  {tool.enabled ? <ToggleRight size={16} aria-hidden="true" /> : <ToggleLeft size={16} aria-hidden="true" />}
                  {tool.enabled ? "Disable" : "Enable"}
                </button>
                <button className="command-button" type="button" onClick={() => setValidation(`${tool.name} test queued through governed execution`)}>
                  <Play size={16} aria-hidden="true" />
                  Test
                </button>
              </div>
              <JsonPreview value={{ configuration: tool.parameterSchema, authorization: tool.permissionPolicy }} />
            </article>
          ))}
        </section>
        <section className="stack">
          <h3>Skills</h3>
          {skills.length === 0 ? <EmptyState title="No skills" /> : null}
          {skills.map(skill => (
            <article className="list-item" key={skill.id}>
              <strong>{skill.name}</strong>
              <span>{skill.version} · {skill.sourceType} · {skill.source}</span>
              <div className="button-row">
                <StatusBadge value={skill.status} />
                <button className="command-button" type="button" onClick={() => void skillAction(skill, skill.status === "ENABLED" ? "disable" : "enable")}>
                  {skill.status === "ENABLED" ? <ToggleRight size={16} aria-hidden="true" /> : <ToggleLeft size={16} aria-hidden="true" />}
                  {skill.status === "ENABLED" ? "Disable" : "Enable"}
                </button>
                <button className="command-button" type="button" onClick={() => void validateSkill(skill)}>
                  <Play size={16} aria-hidden="true" />
                  Test
                </button>
                <button className="command-button" type="button" onClick={() => void skillAction(skill, "rollback")}>
                  <RotateCcw size={16} aria-hidden="true" />
                  Rollback
                </button>
                <button className="command-button" type="button" onClick={() => void skillAction(skill, "lock")}>
                  <Lock size={16} aria-hidden="true" />
                  Lock
                </button>
              </div>
              <JsonPreview value={{ triggers: skill.triggers, permissions: skill.permissions, resources: skill.resources }} />
            </article>
          ))}
        </section>
      </div>
    </section>
  );
}

export function AgentConfigWorkspace({ api }: { api: ApiClient }) {
  const [agents, setAgents] = useState<AgentManagementView[]>([]);
  const [tools, setTools] = useState<ToolDefinition[]>([]);
  const [skills, setSkills] = useState<PersonalSkill[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState(DEFAULT_AGENT_ID);
  const [prompt, setPrompt] = useState("");
  const [modelProvider, setModelProvider] = useState("");
  const [modelName, setModelName] = useState("");
  const [workspace, setWorkspace] = useState("");
  const [budgetIters, setBudgetIters] = useState("3");
  const [compaction, setCompaction] = useState(false);
  const [memoryPolicy, setMemoryPolicy] = useState("confirm durable memory writes");
  const [workspacePolicy, setWorkspacePolicy] = useState("authorized personal workspace only");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const selectedAgent = useMemo(
    () => agents.find(agent => agent.agentId === selectedAgentId) ?? agents[0],
    [agents, selectedAgentId]
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      const [nextAgents, nextTools, nextSkills] = await Promise.all([
        api.listAgents(),
        api.listTools(),
        api.listPersonalSkills()
      ]);
      setAgents(nextAgents);
      setTools(nextTools);
      setSkills(nextSkills);
      setSelectedAgentId(previous => previous || nextAgents[0]?.agentId || DEFAULT_AGENT_ID);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!selectedAgent) {
      return;
    }
    setPrompt(selectedAgent.systemPrompt);
    setModelProvider(selectedAgent.modelProvider);
    setModelName(selectedAgent.modelName);
    setWorkspace(selectedAgent.workspace);
    setBudgetIters(String(selectedAgent.maxIters));
    setCompaction(selectedAgent.compaction);
  }, [selectedAgent]);

  async function savePrompt() {
    if (!selectedAgent) {
      return;
    }
    await mutate(async () => {
      const next = await api.updateAgentPrompt(selectedAgent.agentId, prompt);
      setAgents(previous => previous.map(agent => (agent.agentId === next.agentId ? next : agent)));
    });
  }

  async function saveRuntime() {
    if (!selectedAgent) {
      return;
    }
    await mutate(async () => {
      const next = await api.updateAgentConfig(selectedAgent.agentId, {
        modelProvider,
        modelName,
        workspace,
        compaction,
        maxIters: Number(budgetIters)
      });
      setAgents(previous => previous.map(agent => (agent.agentId === next.agentId ? next : agent)));
    });
  }

  async function mutate(action: () => Promise<void>) {
    try {
      setError(null);
      setAccessDenied(null);
      await action();
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  return (
    <section className="main-panel">
      <SectionHeader
        title="Agent configuration"
        actions={
          <IconButton title="Refresh agent configuration" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {loading ? <LoadingState /> : null}
      <div className="detail-layout">
        <div className="list-panel">
          {agents.map(agent => (
            <button
              className={`list-row ${selectedAgent?.agentId === agent.agentId ? "is-active" : ""}`}
              key={agent.agentId}
              type="button"
              onClick={() => setSelectedAgentId(agent.agentId)}
            >
              <strong>{agent.name}</strong>
              <span>{agent.modelProvider}/{agent.modelName}</span>
            </button>
          ))}
        </div>
        <form className="edit-panel" onSubmit={event => event.preventDefault()}>
          <TextField label="Name" value={selectedAgent?.name ?? ""} onChange={() => undefined} />
          <TextAreaField label="System prompt" value={prompt} onChange={setPrompt} rows={5} />
          <button className="command-button command-button-primary" type="button" onClick={() => void savePrompt()}>
            <Save size={16} aria-hidden="true" />
            Save prompt
          </button>
          <div className="form-grid">
            <TextField label="Model provider" value={modelProvider} onChange={setModelProvider} />
            <TextField label="Model" value={modelName} onChange={setModelName} />
            <TextField label="Budget max iterations" value={budgetIters} onChange={setBudgetIters} type="number" />
            <TextField label="Workspace" value={workspace} onChange={setWorkspace} />
            <TextField label="Workspace policy" value={workspacePolicy} onChange={setWorkspacePolicy} />
            <TextField label="Memory policy" value={memoryPolicy} onChange={setMemoryPolicy} />
          </div>
          <ToggleField label="Context compaction" checked={compaction} onChange={setCompaction} />
          <button className="command-button" type="button" onClick={() => void saveRuntime()}>
            <Save size={16} aria-hidden="true" />
            Save runtime
          </button>
          <div className="split-grid">
            <section className="stack">
              <h3>Tools</h3>
              {tools.map(tool => (
                <div className="list-item" key={tool.id}>
                  <strong>{tool.name}</strong>
                  <StatusBadge value={tool.enabled} />
                </div>
              ))}
            </section>
            <section className="stack">
              <h3>Skills</h3>
              {skills.map(skill => (
                <div className="list-item" key={skill.id}>
                  <strong>{skill.name}</strong>
                  <StatusBadge value={skill.status} />
                </div>
              ))}
            </section>
          </div>
        </form>
      </div>
    </section>
  );
}

export function TraceWorkspace({ api }: { api: ApiClient }) {
  const [traces, setTraces] = useState<OrchestrationTrace[]>([]);
  const [metrics, setMetrics] = useState<OperationalMetricSummary | null>(null);
  const [cost, setCost] = useState<CostUsageReport | null>(null);
  const [toolDiagnostics, setToolDiagnostics] = useState<ToolActivityRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    const results = await Promise.allSettled([
      api.orchestrationTraces(),
      api.metrics(),
      api.cost(DEFAULT_AGENT_ID),
      api.listToolActivity()
    ]);
    if (results[0].status === "fulfilled") {
      setTraces(results[0].value);
    }
    if (results[1].status === "fulfilled") {
      setMetrics(results[1].value);
    }
    if (results[2].status === "fulfilled") {
      setCost(results[2].value);
    }
    if (results[3].status === "fulfilled") {
      setToolDiagnostics(results[3].value);
    }
    const rejected = results.find((result): result is PromiseRejectedResult => result.status === "rejected");
    if (rejected) {
      handleApiError(rejected.reason, setError, setAccessDenied);
    }
    setLoading(false);
  }, [api]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section className="main-panel">
      <SectionHeader
        title="Trace and diagnostics"
        actions={
          <IconButton title="Refresh trace" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {loading ? <LoadingState /> : null}
      <div className="metric-grid">
        <Metric label="Model events" value={metrics?.modelOrAgentEvents ?? 0} />
        <Metric label="Tool calls" value={metrics?.toolCalls ?? toolDiagnostics.length} />
        <Metric label="RAG hits" value={metrics?.ragHits ?? 0} />
        <Metric label="RAG misses" value={metrics?.ragMisses ?? 0} />
        <Metric label="Failures" value={metrics?.failures ?? 0} />
        <Metric label="Cost" value={cost ? formatCost(cost.estimatedCost) : "$0.000"} />
      </div>
      <div className="split-grid">
        <section className="stack">
          <h3>Subagent orchestration</h3>
          {traces.length === 0 ? <EmptyState title="No subagent traces" /> : null}
          {traces.map(trace => (
            <article className="trace-row" key={trace.id}>
              <div className="trace-head">
                <div>
                  <strong>{trace.taskIntent || trace.id}</strong>
                  <span>{formatDateTime(trace.occurredAt)} · selected {trace.selectedAgentId}</span>
                </div>
                <StatusBadge value={trace.status} />
              </div>
              <JsonPreview value={{ steps: trace.steps, handoffs: trace.handoffs, attributes: trace.attributes }} />
            </article>
          ))}
        </section>
        <section className="stack">
          <h3>Tool and RAG diagnostics</h3>
          {toolDiagnostics.length === 0 ? <EmptyState title="No tool diagnostics" /> : null}
          {toolDiagnostics.map(record => (
            <article className="list-item" key={record.id}>
              <strong>{record.toolName}</strong>
              <span>{record.sessionId} · {formatDateTime(record.occurredAt)}</span>
              <StatusBadge value={record.status} />
              <JsonPreview value={{ input: record.sanitizedInput, output: record.sanitizedOutput, failure: record.failureReason }} />
            </article>
          ))}
          <article className="list-item">
            <strong>Actionable diagnostics</strong>
            <span>
              {(metrics?.failures ?? 0) > 0
                ? "Review failed spans and retry with narrower tool scope."
                : "No blocking diagnostics from the latest telemetry snapshot."}
            </span>
          </article>
        </section>
      </div>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{typeof value === "number" ? formatNumber(value) : value}</strong>
    </div>
  );
}

function safeFileName(value: string) {
  return value.replace(/[^a-zA-Z0-9._-]/g, "_") || "attachment";
}

function handleApiError(
  caught: unknown,
  setError: (message: string | null) => void,
  setAccessDenied: (message: string | null) => void
) {
  if (caught instanceof ApiClientError) {
    if (caught.apiError.accessDenied) {
      setAccessDenied(caught.apiError.message);
      return;
    }
    setError(caught.apiError.message);
    return;
  }
  setError(caught instanceof Error ? caught.message : "Request failed.");
}
