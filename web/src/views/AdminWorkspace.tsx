import { useCallback, useEffect, useMemo, useState } from "react";
import { Check, PauseCircle, RefreshCcw, Save, ToggleLeft, ToggleRight } from "lucide-react";
import { ApiClient, ApiClientError } from "../api/client";
import type { AgentManagementView, KnowledgeSourceView, SkillVersion, ToolDefinition } from "../api/types";
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
import { formatDateTime } from "../lib/format";

type AdminTab = "agents" | "tools" | "knowledge" | "skills";

export function AdminWorkspace({ api }: { api: ApiClient }) {
  const [tab, setTab] = useState<AdminTab>("agents");
  const [agents, setAgents] = useState<AgentManagementView[]>([]);
  const [tools, setTools] = useState<ToolDefinition[]>([]);
  const [knowledge, setKnowledge] = useState<KnowledgeSourceView[]>([]);
  const [skills, setSkills] = useState<SkillVersion[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState("");
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
    const results = await Promise.allSettled([
      api.listAgents(),
      api.listConsoleTools(),
      api.listKnowledge(),
      api.listSkills()
    ]);
    const [agentsResult, toolsResult, knowledgeResult, skillsResult] = results;
    if (agentsResult.status === "fulfilled") {
      setAgents(agentsResult.value);
      setSelectedAgentId(previous => previous || agentsResult.value[0]?.agentId || "");
    }
    if (toolsResult.status === "fulfilled") {
      setTools(toolsResult.value);
    }
    if (knowledgeResult.status === "fulfilled") {
      setKnowledge(knowledgeResult.value);
    }
    if (skillsResult.status === "fulfilled") {
      setSkills(skillsResult.value);
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

  async function updatePrompt(systemPrompt: string) {
    if (!selectedAgent) {
      return;
    }
    await runAdminMutation(async () => {
      const next = await api.updateAgentPrompt(selectedAgent.agentId, systemPrompt);
      setAgents(previous => previous.map(agent => (agent.agentId === next.agentId ? next : agent)));
    });
  }

  async function updateConfig(payload: Partial<AgentManagementView>) {
    if (!selectedAgent) {
      return;
    }
    await runAdminMutation(async () => {
      const next = await api.updateAgentConfig(selectedAgent.agentId, payload);
      setAgents(previous => previous.map(agent => (agent.agentId === next.agentId ? next : agent)));
    });
  }

  async function setToolEnabled(tool: ToolDefinition, enabled: boolean) {
    await runAdminMutation(async () => {
      const next = await api.setToolEnabled(tool.id, enabled);
      setTools(previous => previous.map(item => (item.id === next.id ? next : item)));
    });
  }

  async function revokeKnowledge(source: KnowledgeSourceView) {
    await runAdminMutation(async () => {
      const next = await api.revokeKnowledge(source.id);
      setKnowledge(previous => previous.map(item => (item.id === next.id ? next : item)));
    });
  }

  async function skillAction(skill: SkillVersion, action: "approve" | "publish" | "disable") {
    await runAdminMutation(async () => {
      const next =
        action === "approve"
          ? await api.approveSkill(skill.id)
          : action === "publish"
            ? await api.publishSkill(skill.id)
            : await api.disableSkill(skill.id);
      setSkills(previous => previous.map(item => (item.id === next.id ? next : item)));
    });
  }

  async function runAdminMutation(action: () => Promise<void>) {
    setError(null);
    setAccessDenied(null);
    try {
      await action();
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  return (
    <section className="main-panel">
      <SectionHeader
        title="Administration"
        actions={
          <IconButton title="Refresh administration" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      <div className="segmented" role="tablist" aria-label="Administration views">
        {(["agents", "tools", "knowledge", "skills"] as AdminTab[]).map(item => (
          <button
            key={item}
            className={tab === item ? "is-active" : ""}
            role="tab"
            type="button"
            aria-selected={tab === item}
            onClick={() => setTab(item)}
          >
            {item}
          </button>
        ))}
      </div>
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {loading ? <LoadingState /> : null}
      {tab === "agents" ? (
        <AgentAdmin
          agents={agents}
          selectedAgent={selectedAgent}
          selectedAgentId={selectedAgentId}
          setSelectedAgentId={setSelectedAgentId}
          updatePrompt={updatePrompt}
          updateConfig={updateConfig}
        />
      ) : null}
      {tab === "tools" ? <ToolAdmin tools={tools} setToolEnabled={setToolEnabled} /> : null}
      {tab === "knowledge" ? <KnowledgeAdmin sources={knowledge} revoke={revokeKnowledge} /> : null}
      {tab === "skills" ? <SkillAdmin skills={skills} action={skillAction} /> : null}
    </section>
  );
}

function AgentAdmin({
  agents,
  selectedAgent,
  selectedAgentId,
  setSelectedAgentId,
  updatePrompt,
  updateConfig
}: {
  agents: AgentManagementView[];
  selectedAgent?: AgentManagementView;
  selectedAgentId: string;
  setSelectedAgentId: (agentId: string) => void;
  updatePrompt: (systemPrompt: string) => Promise<void>;
  updateConfig: (payload: Partial<AgentManagementView>) => Promise<void>;
}) {
  const [prompt, setPrompt] = useState("");
  const [modelProvider, setModelProvider] = useState("");
  const [modelName, setModelName] = useState("");
  const [workspace, setWorkspace] = useState("");
  const [compaction, setCompaction] = useState(false);
  const [maxIters, setMaxIters] = useState("3");

  useEffect(() => {
    if (selectedAgent) {
      setPrompt(selectedAgent.systemPrompt);
      setModelProvider(selectedAgent.modelProvider);
      setModelName(selectedAgent.modelName);
      setWorkspace(selectedAgent.workspace);
      setCompaction(selectedAgent.compaction);
      setMaxIters(String(selectedAgent.maxIters));
    }
  }, [selectedAgent]);

  if (agents.length === 0) {
    return <EmptyState title="No agents" />;
  }

  return (
    <div className="admin-layout">
      <div className="list-panel">
        {agents.map(agent => (
          <button
            className={`list-row ${agent.agentId === selectedAgentId ? "is-active" : ""}`}
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
        <TextAreaField label="System prompt" value={prompt} onChange={setPrompt} rows={5} />
        <button className="command-button command-button-primary" type="button" onClick={() => void updatePrompt(prompt)}>
          <Save size={16} aria-hidden="true" />
          Save prompt
        </button>
        <div className="form-grid">
          <TextField label="Provider" value={modelProvider} onChange={setModelProvider} />
          <TextField label="Model" value={modelName} onChange={setModelName} />
          <TextField label="Workspace" value={workspace} onChange={setWorkspace} />
          <TextField label="Max iterations" value={maxIters} onChange={setMaxIters} type="number" />
        </div>
        <ToggleField label="Compaction" checked={compaction} onChange={setCompaction} />
        <button
          className="command-button"
          type="button"
          onClick={() =>
            void updateConfig({
              modelProvider,
              modelName,
              workspace,
              compaction,
              maxIters: Number(maxIters)
            })
          }
        >
          <Save size={16} aria-hidden="true" />
          Save runtime
        </button>
      </form>
    </div>
  );
}

function ToolAdmin({
  tools,
  setToolEnabled
}: {
  tools: ToolDefinition[];
  setToolEnabled: (tool: ToolDefinition, enabled: boolean) => Promise<void>;
}) {
  if (tools.length === 0) {
    return <EmptyState title="No tools" />;
  }
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Risk</th>
            <th>Source</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {tools.map(tool => (
            <tr key={tool.id}>
              <td>
                <strong>{tool.name}</strong>
                <span>{tool.description}</span>
              </td>
              <td><StatusBadge value={tool.riskLevel} /></td>
              <td>{tool.sourceType}</td>
              <td><StatusBadge value={tool.enabled} /></td>
              <td>
                <IconButton title={tool.enabled ? "Disable tool" : "Enable tool"} onClick={() => void setToolEnabled(tool, !tool.enabled)}>
                  {tool.enabled ? <ToggleRight size={16} /> : <ToggleLeft size={16} />}
                </IconButton>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function KnowledgeAdmin({
  sources,
  revoke
}: {
  sources: KnowledgeSourceView[];
  revoke: (source: KnowledgeSourceView) => Promise<void>;
}) {
  if (sources.length === 0) {
    return <EmptyState title="No knowledge sources" />;
  }
  return (
    <div className="item-grid">
      {sources.map(source => (
        <article className="item-card" key={source.id}>
          <div className="item-card-header">
            <strong>{source.title}</strong>
            <StatusBadge value={source.status} />
          </div>
          <span>{source.version} · {source.visibility}</span>
          <span>Updated {formatDateTime(source.updatedAt)}</span>
          <JsonPreview value={{ departments: source.allowedDepartments, roles: source.allowedRoles, users: source.allowedUsers }} />
          <div className="button-row">
            <button className="command-button" type="button" disabled={source.status !== "ACTIVE"} onClick={() => void revoke(source)}>
              <PauseCircle size={16} aria-hidden="true" />
              Revoke
            </button>
            <button className="command-button" type="button" disabled title="Console delete endpoint is not exposed">
              Delete
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

function SkillAdmin({
  skills,
  action
}: {
  skills: SkillVersion[];
  action: (skill: SkillVersion, action: "approve" | "publish" | "disable") => Promise<void>;
}) {
  if (skills.length === 0) {
    return <EmptyState title="No skill versions" />;
  }
  return (
    <div className="item-grid">
      {skills.map(skill => (
        <article className="item-card" key={skill.id}>
          <div className="item-card-header">
            <strong>{skill.skillName}</strong>
            <StatusBadge value={skill.status} />
          </div>
          <span>{skill.version} · {skill.repository || "local"}</span>
          <span>Owner {skill.ownerId} · {formatDateTime(skill.updatedAt)}</span>
          <div className="button-row">
            <button className="command-button" type="button" disabled={skill.status !== "PROPOSED"} onClick={() => void action(skill, "approve")}>
              <Check size={16} aria-hidden="true" />
              Approve
            </button>
            <button className="command-button" type="button" disabled={skill.status !== "APPROVED"} onClick={() => void action(skill, "publish")}>
              Publish
            </button>
            <button className="command-button" type="button" disabled={skill.status === "DISABLED"} onClick={() => void action(skill, "disable")}>
              Disable
            </button>
            <button className="command-button" type="button" disabled title="Skill rollback is not exposed by REST API">
              Rollback
            </button>
          </div>
        </article>
      ))}
    </div>
  );
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
