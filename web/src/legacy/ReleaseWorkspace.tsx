import { useCallback, useEffect, useState } from "react";
import { RefreshCcw } from "lucide-react";
import { ApiClient, ApiClientError } from "../api/client";
import type { EndToEndAcceptanceReport, ReadinessCheck, ReleaseScenario, RollbackAction } from "../api/types";
import {
  AccessDenied,
  EmptyState,
  ErrorState,
  IconButton,
  LoadingState,
  SectionHeader,
  StatusBadge
} from "../components/common";

export function ReleaseWorkspace({ api }: { api: ApiClient }) {
  const [scenario, setScenario] = useState<ReleaseScenario | null>(null);
  const [readinessChecks, setReadinessChecks] = useState<ReadinessCheck[]>([]);
  const [rollback, setRollback] = useState<RollbackAction[]>([]);
  const [acceptance, setAcceptance] = useState<EndToEndAcceptanceReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAccessDenied(null);
    try {
      const [nextScenario, nextReadinessChecks, nextRollback, nextAcceptance] = await Promise.all([
        api.releaseScenario(),
        api.readinessChecks(),
        api.rollbackActions(),
        api.acceptance()
      ]);
      setScenario(nextScenario);
      setReadinessChecks(nextReadinessChecks);
      setRollback(nextRollback);
      setAcceptance(nextAcceptance);
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
        title="Release readiness"
        actions={
          <IconButton title="Refresh release readiness" onClick={() => void load()}>
            <RefreshCcw size={16} />
          </IconButton>
        }
      />
      {accessDenied ? <AccessDenied message={accessDenied} /> : null}
      {error ? <ErrorState message={error} /> : null}
      {loading ? <LoadingState /> : null}
      {scenario ? (
        <section className="summary-band summary-band-text">
          <div>
            <span>MVP scenario</span>
            <strong>{scenario.scenario}</strong>
          </div>
          <ul>
            {scenario.acceptanceCriteria.map(item => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>
      ) : null}
      <div className="item-grid">
        {readinessChecks.map(gate => (
          <article className="item-card" key={gate.name}>
            <div className="item-card-header">
              <strong>{gate.name}</strong>
              <StatusBadge value={gate.status} />
            </div>
            <span>{gate.rollbackSwitch}</span>
            <ul>
              {gate.checks.map(check => (
                <li key={check}>{check}</li>
              ))}
            </ul>
          </article>
        ))}
      </div>
      {rollback.length === 0 ? <EmptyState title="No rollback actions" /> : null}
      <div className="item-grid">
        {rollback.map(action => (
          <article className="item-card" key={`${action.capability}-${action.action}`}>
            <strong>{action.capability}</strong>
            <span>{action.action}</span>
            <span>{action.auditRequirement}</span>
          </article>
        ))}
      </div>
      {acceptance ? (
        <section className="checklist">
          <h3>Acceptance</h3>
          {Object.entries({
            tenantIsolation: acceptance.tenantIsolation,
            permissionFiltering: acceptance.permissionFiltering,
            highRiskConfirmation: acceptance.highRiskConfirmation,
            auditTraceability: acceptance.auditTraceability,
            operationalObservability: acceptance.operationalObservability
          }).map(([key, passed]) => (
            <div className="check-row" key={key}>
              <span>{key}</span>
              <StatusBadge value={passed ? "PASSED" : "BLOCKED"} />
            </div>
          ))}
        </section>
      ) : null}
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
