import { describe, expect, it } from "vitest";
import { getNavigationItems } from "./navigation";

describe("role-aware navigation", () => {
  it("keeps restricted navigation unavailable for an employee identity", () => {
    const items = getNavigationItems(["employee"]);

    expect(items.find(item => item.id === "chat")?.enabled).toBe(true);
    expect(items.find(item => item.id === "admin")?.enabled).toBe(false);
    expect(items.find(item => item.id === "operations")?.enabled).toBe(false);
    expect(items.find(item => item.id === "audit")?.enabled).toBe(false);
  });

  it("enables privileged sections according to backend roles", () => {
    const items = getNavigationItems(["admin", "ops", "auditor"]);

    expect(items.find(item => item.id === "admin")?.enabled).toBe(true);
    expect(items.find(item => item.id === "operations")?.enabled).toBe(true);
    expect(items.find(item => item.id === "audit")?.enabled).toBe(true);
  });
});
