import {
  Activity,
  Bot,
  ClipboardCheck,
  GitBranch,
  MessageSquare,
  ShieldCheck
} from "lucide-react";
import type { ComponentType } from "react";

export type RouteId = "chat" | "admin" | "operations" | "audit" | "release" | "orchestration";

export interface NavigationItem {
  id: RouteId;
  label: string;
  enabled: boolean;
  roles: string[];
  Icon: ComponentType<{ size?: number; strokeWidth?: number }>;
}

const roleSets: Record<RouteId, string[]> = {
  chat: [],
  admin: ["admin"],
  operations: ["admin", "ops"],
  audit: ["admin", "auditor"],
  release: ["admin", "ops", "auditor"],
  orchestration: ["admin", "ops", "auditor"]
};

const labels: Record<RouteId, string> = {
  chat: "Chat",
  admin: "Admin",
  operations: "Operations",
  audit: "Audit",
  release: "Release",
  orchestration: "Traces"
};

const icons: Record<RouteId, NavigationItem["Icon"]> = {
  chat: MessageSquare,
  admin: Bot,
  operations: Activity,
  audit: ShieldCheck,
  release: ClipboardCheck,
  orchestration: GitBranch
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
