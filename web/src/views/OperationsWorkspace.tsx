import { FormEvent, useCallback, useEffect, useState } from "react";
import { RefreshCcw, Search } from "lucide-react";
import { ApiClient, ApiClientError } from "../api/client";
import type { ConsoleAuditResult, CostUsageReport, OperationalMetricSummary } from "../api/types";
import {
  AccessDenied,
  EmptyState,
  ErrorState,
  IconButton,
  JsonPreview,
  LoadingState,
  SectionHeader,
  StatusBadge,
  TextField
} from "../components/common";
import { formatCost, formatDateTime, formatNumber } from "../lib/format";

export function OperationsWorkspace({ api }: { api: ApiClient }) {
  const [metrics, setMetrics] = useState<OperationalMetricSummary | null>(null);
  const [cost, setCost] = useState<CostUsageReport | null>(null);
  const [agentFilter, setAgentFilter] = useState("");
  const [providerFilter, setProviderFilter] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      const [nextMetrics, nextCost] = await Promise.all([
        api.metrics(),
        api.cost(agentFilter || undefined, providerFilter || undefined)
      ]);
      setMetrics(nextMetrics);
      setCost(nextCost);
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }, [agentFilter, api, providerFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section className="main-panel">
      <SectionHeader
        title="Operations"
        actions={
          <IconButton title="Refresh operations" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {loading ? <LoadingState /> : null}
      <form className="filter-bar" onSubmit={(event: FormEvent) => { event.preventDefault(); void load(); }}>
        <TextField label="Agent filter" value={agentFilter} onChange={setAgentFilter} />
        <TextField label="Provider filter" value={providerFilter} onChange={setProviderFilter} />
        <button className="command-button command-button-primary" type="submit">
          <Search size={16} aria-hidden="true" />
          Filter
        </button>
      </form>
      {metrics ? (
        <div className="metric-grid">
          <Metric label="Sessions" value={metrics.sessionCount} />
          <Metric label="Model events" value={metrics.modelOrAgentEvents} />
          <Metric label="Tool calls" value={metrics.toolCalls} />
          <Metric label="RAG hits" value={metrics.ragHits} />
          <Metric label="RAG misses" value={metrics.ragMisses} />
          <Metric label="Failures" value={metrics.failures} />
          <Metric label="Latency ms" value={metrics.totalDurationMillis} />
          <Metric label="Feedback" value={metrics.feedbackCount} />
        </div>
      ) : (
        <EmptyState title="No metrics" />
      )}
      {cost ? (
        <section className="summary-band">
          <div>
            <span>Token events</span>
            <strong>{formatNumber(cost.tokenEvents)}</strong>
          </div>
          <div>
            <span>Estimated tokens</span>
            <strong>{formatNumber(cost.estimatedTokens)}</strong>
          </div>
          <div>
            <span>Estimated cost</span>
            <strong>{formatCost(cost.estimatedCost)}</strong>
          </div>
        </section>
      ) : null}
    </section>
  );
}

export function AuditWorkspace({ api }: { api: ApiClient }) {
  const [targetUserId, setTargetUserId] = useState("");
  const [sessionId, setSessionId] = useState("");
  const [resourceId, setResourceId] = useState("");
  const [action, setAction] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [result, setResult] = useState<ConsoleAuditResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  async function search(event?: FormEvent) {
    event?.preventDefault();
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      setResult(await api.audit({ targetUserId, sessionId, resourceId, action, from, to }));
    } catch (caught) {
      handleApiError(caught, setError, setAccessDenied);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void search();
    // Initial load only; filters submit explicitly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const count = (result?.toolAudit.length ?? 0) + (result?.securityAudit.length ?? 0);

  return (
    <section className="main-panel">
      <SectionHeader title="Audit search" />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      <form className="filter-bar filter-bar-wide" onSubmit={search}>
        <TextField label="User" value={targetUserId} onChange={setTargetUserId} />
        <TextField label="Session" value={sessionId} onChange={setSessionId} />
        <TextField label="Resource" value={resourceId} onChange={setResourceId} />
        <TextField label="Action" value={action} onChange={setAction} />
        <TextField label="From" value={from} onChange={setFrom} type="datetime-local" />
        <TextField label="To" value={to} onChange={setTo} type="datetime-local" />
        <button className="command-button command-button-primary" type="submit">
          <Search size={16} aria-hidden="true" />
          Search
        </button>
      </form>
      {loading ? <LoadingState /> : null}
      {count === 0 ? <EmptyState title="No audit rows" /> : null}
      {result?.toolAudit.map(record => (
        <article className="audit-row" key={record.id}>
          <div>
            <strong>{record.toolName}</strong>
            <span>{formatDateTime(record.occurredAt)} · {record.userId}</span>
          </div>
          <StatusBadge value={record.status} />
          <JsonPreview value={{ input: record.sanitizedInput, output: record.sanitizedOutput, failure: record.failureReason }} />
        </article>
      ))}
      {result?.securityAudit.map(record => (
        <article className="audit-row" key={record.id}>
          <div>
            <strong>{record.action}</strong>
            <span>{formatDateTime(record.occurredAt)} · {record.userId} · {record.resourceType}</span>
          </div>
          <JsonPreview value={record.sanitizedDetails} />
        </article>
      ))}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{formatNumber(value)}</strong>
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
