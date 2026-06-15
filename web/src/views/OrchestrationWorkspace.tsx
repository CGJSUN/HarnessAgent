import { useCallback, useEffect, useState } from "react";
import { RefreshCcw } from "lucide-react";
import { ApiClient, ApiClientError } from "../api/client";
import type { OrchestrationTrace } from "../api/types";
import {
  AccessDenied,
  EmptyState,
  ErrorState,
  IconButton,
  JsonPreview,
  LoadingState,
  SectionHeader,
  StatusBadge
} from "../components/common";
import { formatDateTime } from "../lib/format";

export function OrchestrationWorkspace({ api }: { api: ApiClient }) {
  const [traces, setTraces] = useState<OrchestrationTrace[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      setTraces(await api.orchestrationTraces());
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section className="main-panel">
      <SectionHeader
        title="Orchestration traces"
        eyebrow="Passive load uses /api/orchestration/traces only"
        actions={
          <IconButton title="Refresh traces" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {loading ? <LoadingState /> : null}
      {traces.length === 0 ? <EmptyState title="No traces" /> : null}
      <div className="trace-list">
        {traces.map(trace => (
          <article className="trace-row" key={trace.id}>
            <div className="trace-head">
              <div>
                <strong>{trace.taskIntent || trace.id}</strong>
                <span>{formatDateTime(trace.occurredAt)} · supervisor {trace.supervisorAgentId}</span>
              </div>
              <StatusBadge value={trace.status} />
            </div>
            <div className="summary-band">
              <div>
                <span>Selected agent</span>
                <strong>{trace.selectedAgentId || "-"}</strong>
              </div>
              <div>
                <span>Confidence</span>
                <strong>{Math.round(trace.confidence * 100)}%</strong>
              </div>
              <div>
                <span>Candidates</span>
                <strong>{trace.candidateAgentIds.length}</strong>
              </div>
            </div>
            <div className="trace-details">
              <section>
                <h3>Steps</h3>
                {trace.steps.length === 0 ? <EmptyState title="No steps" /> : null}
                {trace.steps.map(step => (
                  <div className="list-item" key={step.id}>
                    <strong>{step.action}</strong>
                    <span>{step.agentId}</span>
                    <StatusBadge value={step.status} />
                  </div>
                ))}
              </section>
              <section>
                <h3>Handoffs</h3>
                {trace.handoffs.length === 0 ? <EmptyState title="No handoffs" /> : null}
                {trace.handoffs.map(handoff => (
                  <div className="list-item" key={`${handoff.fromAgentId}-${handoff.toAgentId}-${handoff.occurredAt}`}>
                    <strong>{handoff.fromAgentId} {"->"} {handoff.toAgentId}</strong>
                    <span>{handoff.reason}</span>
                  </div>
                ))}
              </section>
            </div>
            <JsonPreview value={trace.attributes} />
          </article>
        ))}
      </div>
    </section>
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
