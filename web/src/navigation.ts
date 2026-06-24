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
  Icon: ComponentType<{ size?: number; strokeWidth?: number }>;
}

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

export function getNavigationItems(): NavigationItem[] {
  return (Object.keys(labels) as RouteId[]).map(id => ({
    id,
    label: labels[id],
    enabled: true,
    Icon: icons[id]
  }));
}
