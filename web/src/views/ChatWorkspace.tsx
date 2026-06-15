import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Pause, Play, RefreshCcw, Send, Trash2, X } from "lucide-react";
import { ApiClient, ApiClientError } from "../api/client";
import type {
  ChatMessage,
  LocalIdentity,
  SessionSummary,
  ToolConfirmationView,
  ToolStatusView,
  UserConsoleView
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
  ToggleField
} from "../components/common";
import { formatDateTime } from "../lib/format";

const DEFAULT_AGENT_ID = "enterprise-assistant";

function nextSessionId(identity: LocalIdentity) {
  return `session-${identity.userId || "user"}-${Date.now()}`;
}

function localMessage(role: ChatMessage["role"], content: string, status?: ChatMessage["status"]): ChatMessage {
  return {
    id: crypto.randomUUID(),
    role,
    content,
    createdAt: new Date().toISOString(),
    status
  };
}

export function ChatWorkspace({ api, identity }: { api: ApiClient; identity: LocalIdentity }) {
  const [agentId, setAgentId] = useState(DEFAULT_AGENT_ID);
  const [sessionId, setSessionId] = useState(() => nextSessionId(identity));
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [consoleView, setConsoleView] = useState<UserConsoleView | null>(null);
  const [message, setMessage] = useState("");
  const [knowledgeEnabled, setKnowledgeEnabled] = useState(true);
  const [knowledgeLimit, setKnowledgeLimit] = useState(4);
  const [loading, setLoading] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [runtimeStatus, setRuntimeStatus] = useState("idle");
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);
  const [dismissedPrompts, setDismissedPrompts] = useState<Set<string>>(new Set());
  const abortRef = useRef<AbortController | null>(null);
  const activeAssistantId = useRef<string | null>(null);
  const initializedSession = useRef(false);

  const visiblePrompts = useMemo(
    () => (consoleView?.confirmationPrompts ?? []).filter(prompt => !dismissedPrompts.has(promptKey(prompt))),
    [consoleView, dismissedPrompts]
  );

  const loadConsole = useCallback(
    async (nextSessionId = sessionId) => {
      try {
        const view = await api.getUserConsole(agentId, nextSessionId);
        setConsoleView(view);
      } catch (caught) {
        handleApiError(caught, setError, setAccessDenied);
      }
    },
    [agentId, api, sessionId]
  );

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      const nextSessions = await api.listSessions(agentId);
      setSessions(nextSessions);
      if (
        !initializedSession.current &&
        nextSessions.length > 0 &&
        !nextSessions.some(session => session.sessionId === sessionId)
      ) {
        setSessionId(nextSessions[0].sessionId);
      }
      initializedSession.current = true;
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }, [agentId, api, sessionId]);

  const loadMessages = useCallback(
    async (targetSessionId: string) => {
      setLoading(true);
      setError(null);
      setAccessDenied(null);
      try {
        setMessages(await api.listMessages(agentId, targetSessionId));
        await loadConsole(targetSessionId);
      } catch (caught) {
        handleApiError(caught, setError, setAccessDenied);
      } finally {
        setLoading(false);
      }
    },
    [agentId, api, loadConsole]
  );

  useEffect(() => {
    void loadSessions();
    void loadConsole(sessionId);
  }, [loadConsole, loadSessions, sessionId]);

  useEffect(() => {
    if (sessionId) {
      void loadMessages(sessionId);
    }
  }, [loadMessages, sessionId]);

  async function sendMessage() {
    const trimmed = message.trim();
    if (!trimmed || streaming) {
      return;
    }
    const userMessage = localMessage("USER", trimmed, "done");
    const assistantMessage = localMessage("ASSISTANT", "", "streaming");
    activeAssistantId.current = assistantMessage.id;
    setMessages(previous => [...previous, userMessage, assistantMessage]);
    setMessage("");
    setStreaming(true);
    setRuntimeStatus("started");
    setError(null);
    setAccessDenied(null);
    const controller = new AbortController();
    abortRef.current = controller;

    try {
      await api.streamChat(
        {
          agentId,
          sessionId,
          message: trimmed,
          knowledgeEnabled,
          knowledgeLimit
        },
        event => {
          setRuntimeStatus(event.type === "done" ? "completed" : event.type);
          if (event.type === "delta") {
            patchActiveAssistant(content => content + event.content, event.terminal ? "done" : "streaming");
          }
          if (event.type === "tool") {
            patchActiveAssistant(content => `${content}\n[tool] ${event.content}`.trim(), "streaming");
          }
          if (event.type === "error") {
            patchActiveAssistant(content => `${content}\n${event.content}`.trim(), "failed");
          }
          if (event.terminal || event.type === "done") {
            patchActiveAssistant(content => content, "done");
          }
          if (event.noAnswerReason || event.citations?.length) {
            patchActiveAssistantMetadata({
              noAnswerReason: event.noAnswerReason,
              citations: event.citations
            });
          }
        },
        controller.signal
      );
      await loadSessions();
      await loadConsole(sessionId);
    } catch (caught) {
      if (controller.signal.aborted) {
        patchActiveAssistant(content => content, "cancelled");
        setRuntimeStatus("cancelled");
      } else {
        patchActiveAssistant(content => content, "failed");
        handleApiError(caught, setError, setAccessDenied);
        setRuntimeStatus("failed");
      }
    } finally {
      setStreaming(false);
      abortRef.current = null;
      activeAssistantId.current = null;
    }
  }

  function patchActiveAssistant(update: (content: string) => string, status: ChatMessage["status"]) {
    const id = activeAssistantId.current;
    if (!id) {
      return;
    }
    setMessages(previous =>
      previous.map(item => (item.id === id ? { ...item, content: update(item.content), status } : item))
    );
  }

  function patchActiveAssistantMetadata(metadata: Pick<ChatMessage, "citations" | "noAnswerReason">) {
    const id = activeAssistantId.current;
    if (!id) {
      return;
    }
    setMessages(previous =>
      previous.map(item =>
        item.id === id
          ? {
              ...item,
              citations: metadata.citations ?? item.citations,
              noAnswerReason: metadata.noAnswerReason ?? item.noAnswerReason
            }
          : item
      )
    );
  }

  function startNewSession() {
    const next = nextSessionId(identity);
    setSessionId(next);
    setMessages([]);
    setConsoleView(null);
    setRuntimeStatus("idle");
    setError(null);
    setAccessDenied(null);
  }

  async function removeSession(target: SessionSummary) {
    await api.deleteSession(target.agentId, target.sessionId);
    if (target.sessionId === sessionId) {
      setMessages([]);
      setSessionId(nextSessionId(identity));
    }
    await loadSessions();
  }

  async function confirmTool(prompt: ToolConfirmationView, confirmed: boolean) {
    try {
      const result = confirmed
        ? await api.executeToolConfirmation(agentId, prompt, true)
        : await api.rejectToolConfirmation(agentId, prompt);
      setRuntimeStatus(result.status);
      setDismissedPrompts(previous => new Set(previous).add(promptKey(prompt)));
      await loadConsole(sessionId);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    }
  }

  return (
    <div className="workspace-grid workspace-grid-chat">
      <aside className="sidebar-panel" aria-label="Sessions">
        <SectionHeader
          title="Sessions"
          actions={
            <IconButton title="Refresh sessions" onClick={() => void loadSessions()}>
              <RefreshCcw size={16} />
            </IconButton>
          }
        />
        <label className="field">
          <span>Agent</span>
          <input value={agentId} onChange={event => setAgentId(event.target.value)} />
        </label>
        <button className="command-button" type="button" onClick={startNewSession}>
          New session
        </button>
        <div className="session-list">
          {sessions.length === 0 ? (
            <EmptyState title="No sessions" />
          ) : (
            sessions.map(session => (
              <div
                key={session.sessionId}
                className={`session-row ${session.sessionId === sessionId ? "is-active" : ""}`}
              >
                <button className="session-select" type="button" onClick={() => setSessionId(session.sessionId)}>
                  <span>{session.sessionId}</span>
                  <small>
                    {session.messageCount} messages · {formatDateTime(session.lastMessageAt)}
                  </small>
                </button>
                <IconButton title="Delete session" onClick={() => void removeSession(session)}>
                  <Trash2 size={14} />
                </IconButton>
              </div>
            ))
          )}
        </div>
      </aside>

      <section className="main-panel">
        <SectionHeader title="Agent workspace" eyebrow={sessionId} />
        {accessDenied ? <AccessDenied message={accessDenied} /> : null}
        {error ? <ErrorState message={error} /> : null}
        {loading ? <LoadingState /> : null}
        <div className="message-thread" aria-live="polite">
          {messages.length === 0 ? (
            <EmptyState title="No messages" detail="Start a session with a focused request." />
          ) : (
            messages.map(item => <MessageBubble key={item.id} message={item} />)
          )}
        </div>
        <div className="composer">
          <div className="composer-options">
            <ToggleField label="RAG" checked={knowledgeEnabled} onChange={setKnowledgeEnabled} />
            <label className="range-field">
              <span>Knowledge results</span>
              <input
                type="range"
                min={1}
                max={10}
                value={knowledgeLimit}
                onChange={event => setKnowledgeLimit(Number(event.target.value))}
              />
              <strong>{knowledgeLimit}</strong>
            </label>
            <StatusBadge value={runtimeStatus} />
          </div>
          <label className="field field-message">
            <span>Message</span>
            <textarea value={message} rows={3} onChange={event => setMessage(event.target.value)} />
          </label>
          <div className="composer-actions">
            <button className="command-button command-button-primary" type="button" disabled={streaming} onClick={sendMessage}>
              <Send size={16} aria-hidden="true" />
              Send
            </button>
            <button
              className="command-button"
              type="button"
              disabled={!streaming}
              onClick={() => abortRef.current?.abort()}
            >
              <Pause size={16} aria-hidden="true" />
              Cancel
            </button>
          </div>
        </div>
      </section>

      <aside className="sidebar-panel" aria-label="Runtime">
        <SectionHeader title="Runtime" />
        <RuntimeTools toolStatus={consoleView?.toolStatus ?? []} prompts={visiblePrompts} onConfirm={confirmTool} />
        <section className="stack">
          <h3>Citations</h3>
          {(consoleView?.latestCitations ?? []).length === 0 ? (
            <EmptyState title="No citations" detail="Streaming responses keep partial output even when retrieval evidence is absent." />
          ) : (
            consoleView?.latestCitations.map(citation => (
              <div className="list-item" key={`${citation.sourceId}-${citation.chunkId}`}>
                <strong>{citation.title}</strong>
                <span>
                  {citation.version} · chunk {citation.chunkIndex}
                </span>
              </div>
            ))
          )}
        </section>
      </aside>
    </div>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  return (
    <article className={`message message-${message.role.toLowerCase()}`}>
      <div className="message-meta">
        <strong>{message.role.toLowerCase()}</strong>
        <span>{formatDateTime(message.createdAt)}</span>
        {message.status ? <StatusBadge value={message.status} /> : null}
      </div>
      <p>{message.content || " "}</p>
      {message.noAnswerReason ? <div className="no-answer">No answer: {message.noAnswerReason}</div> : null}
      {message.citations?.length ? (
        <div className="citation-list">
          {message.citations.map(citation => (
            <span className="citation-chip" key={`${citation.sourceId}-${citation.chunkId}`}>
              {citation.title} {citation.version}
            </span>
          ))}
        </div>
      ) : null}
    </article>
  );
}

function RuntimeTools({
  toolStatus,
  prompts,
  onConfirm
}: {
  toolStatus: ToolStatusView[];
  prompts: ToolConfirmationView[];
  onConfirm: (prompt: ToolConfirmationView, confirmed: boolean) => void;
}) {
  return (
    <section className="stack">
      <h3>Tools</h3>
      {toolStatus.length === 0 ? (
        <EmptyState title="No tool runs" />
      ) : (
        toolStatus.map(tool => (
          <div className="list-item" key={`${tool.toolId}-${tool.sessionId}`}>
            <strong>{tool.toolName}</strong>
            <span>{tool.sessionId}</span>
            <StatusBadge value={tool.status} />
          </div>
        ))
      )}
      {prompts.map(prompt => (
        <div className="confirmation" key={promptKey(prompt)}>
          <div>
            <strong>{prompt.toolName}</strong>
            <span>{prompt.sessionId}</span>
          </div>
          {prompt.operationSummary ? <JsonPreview value={prompt.operationSummary} /> : null}
          <JsonPreview value={prompt.sanitizedInput} />
          <div className="button-row">
            <button className="command-button command-button-primary" type="button" onClick={() => onConfirm(prompt, true)}>
              <Play size={16} aria-hidden="true" />
              Confirm
            </button>
            <button className="command-button" type="button" onClick={() => onConfirm(prompt, false)}>
              <X size={16} aria-hidden="true" />
              Reject
            </button>
          </div>
        </div>
      ))}
    </section>
  );
}

function promptKey(prompt: ToolConfirmationView) {
  return `${prompt.toolId}:${prompt.sessionId}:${JSON.stringify(prompt.sanitizedInput)}`;
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
