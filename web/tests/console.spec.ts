import { expect, test, type Page, type Route } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await mockBackend(page);
});

test("loads workspace, sessions, citations, chat stream, and cancel recovery", async ({ page }) => {
  let streamAttempt = 0;
  await page.route("**/api/chat/stream", async route => {
    streamAttempt += 1;
    if (streamAttempt === 1) {
      await delay(800);
      await route
        .fulfill({
          status: 200,
          contentType: "text/event-stream",
          body: sse([
            ["delta", { type: "delta", content: "late", terminal: false }],
            ["done", { type: "done", content: "completed", terminal: true }]
          ])
        })
        .catch(() => undefined);
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: sse([
        ["status", { type: "status", content: "started", terminal: false }],
        ["delta", { type: "delta", content: "Enterprise answer", terminal: false }],
        ["done", { type: "done", content: "completed", terminal: true }]
      ])
    });
  });

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Agent workspace" })).toBeVisible();
  await expect(page.getByRole("button", { name: /session-1/ })).toBeVisible();
  await expect(page.getByText("Runbook")).toBeVisible();
  await expect(page.getByText(/v1 .* chunk 0/)).toBeVisible();
  await expect(page.getByRole("complementary", { name: "Runtime" }).getByText("finance.transfer").first()).toBeVisible();
  await expect(page.getByText("insufficient evidence")).toBeVisible();

  await page.getByLabel("Message").fill("cancel this run");
  await page.getByRole("button", { name: "Send" }).click();
  await page.getByRole("button", { name: "Cancel" }).click();
  await expect(page.locator(".message-assistant").getByText("cancelled").first()).toBeVisible();

  await page.getByLabel("Message").fill("answer with citations");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Enterprise answer")).toBeVisible();
  await expect(page.getByText("completed")).toBeVisible();

  await page.screenshot({ path: `test-results/${test.info().project.name}-chat.png`, fullPage: true });
});

test("loads an admin view and an operations view", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Admin" }).click();
  await expect(page.getByRole("heading", { name: "Administration" })).toBeVisible();
  await expect(page.getByText("enterprise-assistant")).toBeVisible();
  await page.getByRole("tab", { name: "tools" }).click();
  await expect(page.getByText("search.docs")).toBeVisible();
  await page.getByRole("tab", { name: "skills" }).click();
  await expect(page.getByRole("button", { name: "Rollback" }).first()).toBeDisabled();

  await page.getByRole("button", { name: "Operations" }).click();
  await expect(page.getByRole("heading", { name: "Operations" })).toBeVisible();
  await expect(page.getByText("Token events")).toBeVisible();
  await expect(page.getByText("$0.042")).toBeVisible();

  await page.getByRole("button", { name: "Audit" }).click();
  await expect(page.getByRole("heading", { name: "Audit search" })).toBeVisible();
  await expect(page.getByText("EXECUTE_TOOL")).toBeVisible();

  await page.screenshot({ path: `test-results/${test.info().project.name}-admin-ops.png`, fullPage: true });
});

test("shows backend errors for privileged mutations", async ({ page }) => {
  await page.route("**/api/console/agents/*/prompt?**", route =>
    route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        message: "admin role is required",
        timestamp: "2026-06-14T00:00:00Z"
      })
    })
  );

  await page.goto("/");
  await page.getByRole("button", { name: "Admin" }).click();
  await expect(page.getByRole("heading", { name: "Administration" })).toBeVisible();
  await page.getByRole("button", { name: "Save prompt" }).click();

  await expect(page.getByText("Access denied")).toBeVisible();
  await expect(page.getByText("admin role is required")).toBeVisible();
});

async function mockBackend(page: Page) {
  await page.route("**/api/sessions?**", route =>
    json(route, [
      {
        tenantId: "tenant-a",
        userId: "admin-a",
        agentId: "enterprise-assistant",
        sessionId: "session-1",
        messageCount: 2,
        lastMessageAt: "2026-06-14T00:00:00Z"
      }
    ])
  );
  await page.route("**/api/messages?**", route =>
    json(route, [
      {
        id: "m1",
        role: "USER",
        content: "What is the rollout state?",
        createdAt: "2026-06-14T00:00:00Z"
      },
      {
        id: "m2",
        role: "ASSISTANT",
        content: "No answer is available from current evidence.",
        createdAt: "2026-06-14T00:00:01Z",
        noAnswerReason: "insufficient evidence"
      }
    ])
  );
  await page.route("**/api/console/user?**", route =>
    json(route, {
      sessions: [],
      messages: [],
      latestCitations: [
        {
          sourceId: "source-1",
          title: "Runbook",
          version: "v1",
          chunkIndex: 0,
          chunkId: "chunk-1"
        }
      ],
      toolStatus: [
        {
          toolId: "tool-1",
          toolName: "finance.transfer",
          status: "PENDING_CONFIRMATION",
          sessionId: "session-1",
          durationMillis: 10
        }
      ],
      confirmationPrompts: [
        {
          toolId: "tool-1",
          toolName: "finance.transfer",
          sessionId: "session-1",
          sanitizedInput: { amount: "100", token: "[REDACTED]" },
          operationSummary: { toolName: "finance.transfer", riskLevel: "HIGH_RISK", parameters: { amount: "100" } },
          idempotencyKey: "idem-1"
        }
      ],
      fileUploads: []
    })
  );
  await page.route("**/api/console/agents?**", route =>
    json(route, [
      {
        agentId: "enterprise-assistant",
        name: "enterprise-assistant",
        systemPrompt: "Be accurate.",
        modelProvider: "echo",
        modelName: "echo-local",
        workspace: ".harness-agent/workspaces/enterprise-assistant",
        workloadType: "OFFICE",
        compaction: true,
        maxIters: 3
      }
    ])
  );
  await page.route("**/api/console/tools?**", route =>
    json(route, [
      {
        id: "tool-2",
        tenantId: "tenant-a",
        name: "search.docs",
        description: "Search internal documents",
        ownerSystem: "console",
        ownerId: "admin-a",
        sourceType: "INTERNAL",
        sourceRef: "console",
        riskLevel: "READ_ONLY",
        mutating: false,
        enabled: true,
        parameterSchema: { requiredParameters: [], optionalParameters: [], allowedValues: {}, sensitiveParameters: [] },
        permissionPolicy: {
          allowedTenantIds: [],
          allowedUserIds: [],
          allowedAgentIds: [],
          allowedDepartments: [],
          allowedRoles: []
        },
        auditPolicy: { enabled: true, sensitiveParameters: [], sensitiveResultFields: [] },
        createdAt: "2026-06-14T00:00:00Z",
        updatedAt: "2026-06-14T00:00:00Z"
      }
    ])
  );
  await page.route("**/api/console/knowledge?**", route =>
    json(route, [
      {
        id: "source-1",
        title: "Runbook",
        version: "v1",
        visibility: "PUBLIC",
        allowedDepartments: [],
        allowedRoles: [],
        allowedUsers: [],
        status: "ACTIVE",
        indexStatus: "active",
        lastSyncResult: "ok",
        updatedAt: "2026-06-14T00:00:00Z"
      }
    ])
  );
  await page.route("**/api/console/skills?**", route =>
    json(route, [
      {
        id: "skill-1",
        tenantId: "tenant-a",
        skillName: "answer-review",
        version: "1.0.0",
        repository: "git@example/skills",
        ownerId: "admin-a",
        status: "APPROVED",
        approvedBy: "admin-a",
        updatedAt: "2026-06-14T00:00:00Z"
      }
    ])
  );
  await page.route("**/api/console/metrics?**", route =>
    json(route, {
      sessionCount: 5,
      modelOrAgentEvents: 20,
      toolCalls: 3,
      ragHits: 7,
      ragMisses: 1,
      failures: 0,
      totalDurationMillis: 1234,
      feedbackCount: 2
    })
  );
  await page.route("**/api/console/cost?**", route =>
    json(route, {
      tenantId: "tenant-a",
      agentId: "enterprise-assistant",
      providerId: "echo",
      tokenEvents: 2,
      estimatedTokens: 4200,
      estimatedCost: 0.042
    })
  );
  await page.route("**/api/console/audit?**", route =>
    json(route, {
      toolAudit: [],
      securityAudit: [
        {
          id: "audit-1",
          occurredAt: "2026-06-14T00:00:00Z",
          tenantId: "tenant-a",
          userId: "admin-a",
          resourceType: "TOOL",
          resourceId: "tool-1",
          action: "EXECUTE_TOOL",
          sanitizedDetails: { token: "[REDACTED]" }
        }
      ]
    })
  );
  await page.route("**/api/release/**", async route => {
    const url = route.request().url();
    if (url.includes("/scenario")) {
      await json(route, { scenario: "MVP rollout", acceptanceCriteria: ["chat", "admin", "audit"] });
      return;
    }
    if (url.includes("/phase-gates")) {
      await json(route, [{ name: "go-live", status: "PASSED", checks: ["rbac"], rollbackSwitch: "disable-ui" }]);
      return;
    }
    if (url.includes("/rollback")) {
      await json(route, [{ capability: "tooling", action: "disable-tool", auditRequirement: "required" }]);
      return;
    }
    await json(route, {
      tenantIsolation: true,
      permissionFiltering: true,
      highRiskConfirmation: true,
      auditTraceability: true,
      operationalObservability: true,
      notes: []
    });
  });
  await page.route("**/api/orchestration/traces?**", route =>
    json(route, [
      {
        id: "trace-1",
        occurredAt: "2026-06-14T00:00:00Z",
        tenantId: "tenant-a",
        userId: "admin-a",
        supervisorAgentId: "supervisor",
        selectedAgentId: "enterprise-assistant",
        taskIntent: "support",
        confidence: 0.91,
        status: "COMPLETED",
        candidateAgentIds: ["enterprise-assistant"],
        steps: [],
        handoffs: [],
        attributes: { source: "test" }
      }
    ])
  );
}

async function json(route: Route, body: unknown) {
  await route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(body)
  });
}

function sse(events: Array<[string, Record<string, unknown>]>) {
  return events.map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join("");
}

function delay(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
