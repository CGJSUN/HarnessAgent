import { useMemo, useState } from "react";
import { ApiClient } from "./api/client";
import { DEFAULT_IDENTITY } from "./api/identity";
import type { LocalIdentity } from "./api/types";
import { getNavigationItems, type RouteId } from "./navigation";
import { IdentityPanel } from "./views/IdentityPanel";
import { ChatWorkspace } from "./views/ChatWorkspace";
import {
  AgentConfigWorkspace,
  FilesWorkspace,
  KnowledgeWorkspace,
  TasksWorkspace,
  ToolsSkillsWorkspace,
  TraceWorkspace
} from "./views/WorkbenchViews";
import "./styles.css";

export default function App() {
  const [identity, setIdentity] = useState<LocalIdentity>(DEFAULT_IDENTITY);
  const [route, setRoute] = useState<RouteId>("chat");
  const [fileReference, setFileReference] = useState<string>("");
  const api = useMemo(() => new ApiClient({ getIdentity: () => identity }), [identity]);
  const navigation = getNavigationItems();
  const active = navigation.find(item => item.id === route);
  const activeRoute = active?.enabled ? route : "chat";

  function openFileReference(uri: string) {
    if (!uri) {
      return;
    }
    setFileReference(uri);
    setRoute("files");
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">HA</span>
          <div>
            <strong>Personal Agent Workbench</strong>
            <span>{identity.ownerId} · {identity.agentId}</span>
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
              title={item.label}
              onClick={() => setRoute(item.id)}
            >
              <item.Icon size={18} aria-hidden="true" />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
        <main className="content">
          {renderRoute(activeRoute, api, identity, fileReference, openFileReference)}
        </main>
      </div>
    </div>
  );
}

function renderRoute(
  route: RouteId,
  api: ApiClient,
  identity: LocalIdentity,
  fileReference: string,
  openFileReference: (uri: string) => void
) {
  if (route === "tasks") {
    return <TasksWorkspace api={api} />;
  }
  if (route === "files") {
    return <FilesWorkspace api={api} initialReference={fileReference} />;
  }
  if (route === "knowledge") {
    return <KnowledgeWorkspace api={api} />;
  }
  if (route === "tools") {
    return <ToolsSkillsWorkspace api={api} />;
  }
  if (route === "agent") {
    return <AgentConfigWorkspace api={api} />;
  }
  if (route === "trace") {
    return <TraceWorkspace api={api} />;
  }
  return <ChatWorkspace api={api} identity={identity} onOpenFileReference={openFileReference} />;
}
