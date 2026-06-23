import { describe, expect, it } from "vitest";
import { getNavigationItems } from "./navigation";

describe("personal workbench navigation", () => {
  it("exposes every personal workbench section for an employee identity", () => {
    const items = getNavigationItems(["employee"]);

    expect(items.map(item => item.id)).toEqual([
      "chat",
      "tasks",
      "files",
      "knowledge",
      "tools",
      "agent",
      "trace"
    ]);
    expect(items.every(item => item.enabled)).toBe(true);
  });

  it("keeps the workbench labels personal instead of enterprise console labels", () => {
    const labels = getNavigationItems(["employee"]).map(item => item.label);

    expect(labels).toEqual([
      "Chat",
      "Tasks",
      "Files",
      "Knowledge",
      "Tools",
      "Agent",
      "Trace"
    ]);
    expect(labels).not.toContain("Admin");
    expect(labels).not.toContain("Release");
  });
});
