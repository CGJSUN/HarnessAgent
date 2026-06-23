import {
  Bot,
  Brain,
  Folder,
  ListChecks,
  MessageSquare,
  SearchCode,
  Wrench
} from "lucide-react";
import type { ComponentType } from "react";

export type RouteId = "chat" | "tasks" | "files" | "knowledge" | "tools" | "agent" | "trace";

export interface NavigationItem {
  id: RouteId;
  label: string;
  enabled: boolean;
  roles: string[];
  Icon: ComponentType<{ size?: number; strokeWidth?: number }>;
}

const roleSets: Record<RouteId, string[]> = {
  chat: [],
  tasks: [],
  files: [],
  knowledge: [],
  tools: [],
  agent: [],
  trace: []
};

const labels: Record<RouteId, string> = {
  chat: "Chat",
  tasks: "Tasks",
  files: "Files",
  knowledge: "Knowledge",
  tools: "Tools",
  agent: "Agent",
  trace: "Trace"
};

const icons: Record<RouteId, NavigationItem["Icon"]> = {
  chat: MessageSquare,
  tasks: ListChecks,
  files: Folder,
  knowledge: Brain,
  tools: Wrench,
  agent: Bot,
  trace: SearchCode
};

export function canAccess(requiredRoles: string[], userRoles: string[]): boolean {
  if (requiredRoles.length === 0) {
    return true;
  }
  return requiredRoles.some(role => userRoles.includes(role));
}

export function getNavigationItems(userRoles: string[]): NavigationItem[] {
  return (Object.keys(roleSets) as RouteId[]).map(id => ({
    id,
    label: labels[id],
    enabled: canAccess(roleSets[id], userRoles),
    roles: roleSets[id],
    Icon: icons[id]
  }));
}
