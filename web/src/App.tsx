import { useMemo, useState } from "react";
import { ApiClient } from "./api/client";
import { DEFAULT_IDENTITY } from "./api/identity";
import type { LocalIdentity } from "./api/types";
import { getNavigationItems, type RouteId } from "./navigation";
import { AccessDenied } from "./components/common";
import { IdentityPanel } from "./views/IdentityPanel";
import { ChatWorkspace } from "./views/ChatWorkspace";
import { AdminWorkspace } from "./views/AdminWorkspace";
import { OperationsWorkspace, AuditWorkspace } from "./views/OperationsWorkspace";
import { ReleaseWorkspace } from "./views/ReleaseWorkspace";
import { OrchestrationWorkspace } from "./views/OrchestrationWorkspace";
import "./styles.css";

export default function App() {
  const [identity, setIdentity] = useState<LocalIdentity>(DEFAULT_IDENTITY);
  const [route, setRoute] = useState<RouteId>("chat");
  const api = useMemo(() => new ApiClient({ getIdentity: () => identity }), [identity]);
  const navigation = getNavigationItems(identity.roles);
  const active = navigation.find(item => item.id === route);
  const activeRoute = active?.enabled ? route : "chat";

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">HA</span>
          <div>
            <strong>Harness Agent Console</strong>
            <span>{identity.tenantId} · {identity.userId}</span>
          </div>
        </div>
        <IdentityPanel identity={identity} onIdentityChange={setIdentity} />
      </header>
      <div className="app-body">
        <nav className="rail" aria-label="Workspace">
          {navigation.map(item => (
            <button
              key={item.id}
              className={activeRoute === item.id ? "is-active" : ""}
              type="button"
              disabled={!item.enabled}
              title={item.enabled ? item.label : `Requires ${item.roles.join(" or ")}`}
              onClick={() => setRoute(item.id)}
            >
              <item.Icon size={18} aria-hidden="true" />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
        <main className="content">{renderRoute(activeRoute, api, identity, active?.enabled === false)}</main>
      </div>
    </div>
  );
}

function renderRoute(route: RouteId, api: ApiClient, identity: LocalIdentity, wasDenied: boolean) {
  if (wasDenied) {
    return <AccessDenied message="The selected view is not available for the current local role set." />;
  }
  if (route === "admin") {
    return <AdminWorkspace api={api} />;
  }
  if (route === "operations") {
    return <OperationsWorkspace api={api} />;
  }
  if (route === "audit") {
    return <AuditWorkspace api={api} />;
  }
  if (route === "release") {
    return <ReleaseWorkspace api={api} />;
  }
  if (route === "orchestration") {
    return <OrchestrationWorkspace api={api} />;
  }
  return <ChatWorkspace api={api} identity={identity} />;
}
