import { expect, test, type Page, type Route } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await mockBackend(page);
});

test("runs the personal chat workbench with confirmations, typed events, and file references", async ({ page }) => {
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
            ["delta", { type: "delta", kind: "TEXT_DELTA", channel: "USER_VISIBLE", content: "late", terminal: false }],
            ["done", { type: "done", kind: "COMPLETION", channel: "USER_VISIBLE", content: "completed", terminal: true }]
          ])
        })
        .catch(() => undefined);
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: sse([
        ["status", { type: "status", kind: "MODEL_STATUS", channel: "SYSTEM_NOTICE", content: "started", terminal: false }],
        ["delta", { type: "delta", kind: "TEXT_DELTA", channel: "USER_VISIBLE", content: "Personal answer", terminal: false }],
        ["tool", { type: "tool", kind: "TOOL_EVENT", channel: "TOOL_EVENT", content: "search.docs", terminal: false }],
        ["subagent", { type: "subagent", kind: "SUBAGENT_EVENT", channel: "DIAGNOSTIC", content: "research-agent active", terminal: false }],
        [
          "done",
          {
            type: "done",
            kind: "COMPLETION",
            channel: "USER_VISIBLE",
            content: "completed",
            terminal: true,
            citations: [
              {
                sourceId: "source-1",
                title: "Runbook",
                version: "v1",
                chunkIndex: 0,
                chunkId: "chunk-1",
                sourceUri: "workspace://artifacts/runbook.md"
              }
            ]
          }
        ]
      ])
    });
  });

  await page.goto("/");
  await expect(page.getByText("Personal Agent Workbench")).toBeVisible();
  const identityPanel = page.getByRole("region", { name: "Local identity" });
  await expect(identityPanel.getByLabel("Owner")).toBeVisible();
  await expect(identityPanel.getByLabel("Agent")).toBeVisible();
  await expect(page.getByLabel("Owner scope")).toHaveCount(0);
  await expect(page.getByLabel("Owners")).toHaveCount(0);
  await expect(page.getByLabel("Owners")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Owner settings" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Diagnostics" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Readiness" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Agent workspace" })).toBeVisible();
  await expect(page.getByRole("button", { name: /session-1/ })).toBeVisible();
  await expect(page.getByText("analysis.md")).toBeVisible();
  await expect(page.getByText("insufficient evidence")).toBeVisible();
  await expect(page.getByRole("complementary", { name: "Runtime" }).getByText("finance.transfer").first()).toBeVisible();

  await page.getByRole("button", { name: "Confirm" }).click();
  await expect(page.getByText("SUCCEEDED")).toBeVisible();

  await page.getByRole("button", { name: "analysis.md" }).click();
  await expect(page.getByRole("heading", { name: "Workspace files" })).toBeVisible();
  await expect(page.getByText("workspace://artifacts/analysis.md", { exact: true }).last()).toBeVisible();
  await expect(page.getByText("Analysis preview")).toBeVisible();

  await page.getByRole("button", { name: "Chat" }).click();
  await page.getByLabel("Message").fill("cancel this run");
  await page.getByRole("button", { name: "Send" }).click();
  await page.getByRole("button", { name: "Cancel" }).click();
  await expect(page.locator(".message-assistant").getByText("cancelled").first()).toBeVisible();

  await page.getByLabel("Message").fill("answer with citations");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Personal answer", { exact: true }).last()).toBeVisible();
  await expect(page.getByText("SUBAGENT_EVENT")).toBeVisible();
  await expect(page.getByText("research-agent active", { exact: true }).first()).toBeVisible();

  await page.screenshot({ path: `test-results/${test.info().project.name}-workbench-chat.png`, fullPage: true });
});

test("navigates personal workbench sections on desktop and mobile", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Tasks" }).click();
  await expect(page.getByRole("heading", { name: "Tasks and plans" })).toBeVisible();
  await expect(page.getByText("workspace://plans/plan-1.md", { exact: true }).last()).toBeVisible();
  await expect(page.getByText("Collect workspace context", { exact: true }).last()).toBeVisible();

  await page.getByRole("button", { name: "Files" }).click();
  await expect(page.getByRole("heading", { name: "Workspace files" })).toBeVisible();
  await expect(page.getByRole("button", { name: /runbook\.md/ })).toBeVisible();
  await expect(page.getByText("Download")).toBeVisible();
  await expect(page.getByText("Delete")).toBeVisible();

  await page.getByRole("button", { name: "Knowledge" }).click();
  await expect(page.getByRole("heading", { name: "Knowledge and memory" })).toBeVisible();
  await expect(page.getByText("Runbook", { exact: true })).toBeVisible();
  await expect(page.getByText("Preference", { exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "Rebuild index" }).click();
  await expect(page.getByText("Rebuild requested")).toBeVisible();

  await page.getByRole("button", { name: "Tools" }).click();
  await expect(page.getByRole("heading", { name: "Tools and skills" })).toBeVisible();
  await expect(page.getByText("search.docs")).toBeVisible();
  await expect(page.getByText("answer-review", { exact: true })).toBeVisible();
  await expect(page.getByText(/authorization/i)).toBeVisible();

  await page.getByRole("button", { name: "Agent" }).click();
  await expect(page.getByRole("heading", { name: "Agent configuration" })).toBeVisible();
  await expect(page.getByLabel("Budget max iterations")).toBeVisible();
  await expect(page.getByLabel("Workspace policy")).toBeVisible();
  await expect(page.getByText("Skills")).toBeVisible();

  await page.getByRole("button", { name: "Trace" }).click();
  await expect(page.getByRole("heading", { name: "Trace and diagnostics" })).toBeVisible();
  await expect(page.getByText("Model events")).toBeVisible();
  await expect(page.getByText("Tool and RAG diagnostics")).toBeVisible();
  await expect(page.getByText("research handoff", { exact: true })).toBeVisible();
  await expect(page.getByText("Actionable diagnostics")).toBeVisible();

  await page.screenshot({ path: `test-results/${test.info().project.name}-workbench-sections.png`, fullPage: true });
});

async function mockBackend(page: Page) {
  await page.route("**/api/sessions?**", route =>
    json(route, [
      {
        ownerId: "owner-a",
        agentId: "personal-assistant",
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
        content: "What changed?",
        contentBlocks: [{ type: "TEXT", text: "What changed?" }],
        createdAt: "2026-06-14T00:00:00Z",
        status: "done"
      },
      {
        id: "m2",
        role: "ASSISTANT",
        content: "No answer is available from current evidence.",
        contentBlocks: [
          { type: "TEXT", text: "No answer is available from current evidence." },
          { type: "FILE", uri: "workspace://artifacts/analysis.md", title: "analysis.md", mimeType: "text/markdown" }
        ],
        createdAt: "2026-06-14T00:00:01Z",
        status: "done",
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
          chunkId: "chunk-1",
          sourceUri: "workspace://artifacts/runbook.md"
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
          confirmationId: "confirmation-1",
          toolId: "tool-1",
          toolName: "finance.transfer",
          sessionId: "session-1",
          riskLevel: "HIGH_RISK",
          status: "PENDING",
          sanitizedInput: { amount: "100", token: "[REDACTED]" },
          operationSummary: { toolName: "finance.transfer", riskLevel: "HIGH_RISK", parameters: { amount: "100" } },
          idempotencyKey: "idem-1"
        }
      ],
      fileUploads: [{ fileId: "file-1", fileName: "analysis.md", status: "AVAILABLE" }]
    })
  );
  await page.route("**/api/tools/confirmations/confirmation-1/resume", route =>
    json(route, {
      executionId: "execution-1",
      toolId: "tool-1",
      status: "SUCCEEDED",
      message: "confirmed",
      output: {},
      approvalRequired: false,
      operationSummary: {}
    })
  );
  await page.route("**/api/workspace/plans?**", async route => {
    if (route.request().method() === "POST") {
      await json(route, plan());
      return;
    }
    await json(route, [plan()]);
  });
  await page.route("**/api/workspace/files/preview?**", route =>
    json(route, {
      file: {
        uri: route.request().url().includes("analysis.md")
          ? "workspace://artifacts/analysis.md"
          : "workspace://artifacts/runbook.md",
        relativePath: route.request().url().includes("analysis.md") ? "artifacts/analysis.md" : "artifacts/runbook.md",
        fileName: route.request().url().includes("analysis.md") ? "analysis.md" : "runbook.md",
        mimeType: "text/markdown",
        size: 32,
        updatedAt: "2026-06-14T00:00:00Z"
      },
      content: route.request().url().includes("analysis.md") ? "Analysis preview" : "Runbook preview",
      truncated: false
    })
  );
  await page.route("**/api/workspace/files?**", async route => {
    if (route.request().method() === "POST") {
      await json(route, workspaceFile("uploaded.md"));
      return;
    }
    if (route.request().method() === "DELETE") {
      await json(route, { deleted: true });
      return;
    }
    await json(route, [workspaceFile("runbook.md"), workspaceFile("analysis.md")]);
  });
  await page.route("**/api/workspace/files/download?**", route =>
    route.fulfill({ status: 200, contentType: "text/markdown", body: "download" })
  );
  await page.route("**/api/knowledge/sources?**", async route => {
    if (route.request().method() === "DELETE") {
      await json(route, knowledgeSource());
      return;
    }
    await json(route, [knowledgeSource()]);
  });
  await page.route("**/api/knowledge/memory?**", async route => {
    if (route.request().method() === "POST") {
      await json(route, memory("memory-2", "Preference"));
      return;
    }
    await json(route, [memory("memory-1", "Preference")]);
  });
  await page.route("**/api/knowledge/memory/*?**", route => json(route, memory("memory-1", "Preference")));
  await page.route("**/api/knowledge/export?**", route =>
    json(route, {
      ownerId: "owner-a",
      agentId: "personal-assistant",
      memories: [memory("memory-1", "Preference")],
      knowledgeSources: [knowledgeSource()],
      indexMetadata: [],
      citationRecords: []
    })
  );
  await page.route("**/api/tools?**", route => json(route, [toolDefinition()]));
  await page.route("**/api/tools/confirmations?**", route => json(route, []));
  await page.route("**/api/tools/activity?**", route =>
    json(route, [
      {
        id: "activity-1",
        occurredAt: "2026-06-14T00:00:00Z",
        ownerId: "owner-a",
        agentId: "personal-assistant",
        sessionId: "session-1",
        toolId: "tool-2",
        toolName: "search.docs",
        sourceType: "INTERNAL",
        riskLevel: "READ_ONLY",
        status: "SUCCEEDED",
        sanitizedInput: { query: "runbook" },
        sanitizedOutput: { hits: 1 },
        durationMillis: 20,
        idempotencyKey: "idem-tool",
        failureReason: ""
      }
    ])
  );
  await page.route("**/api/console/tools/*/enabled?**", route => json(route, { ...toolDefinition(), enabled: false }));
  await page.route("**/api/skills?**", route => json(route, [personalSkill()]));
  await page.route("**/api/skills/validate-local", route =>
    json(route, { skillName: "answer-review", version: "1.0.0", source: "local", valid: true, errors: [] })
  );
  await page.route("**/api/skills/*/*/**", route => json(route, { ...personalSkill(), status: "DISABLED" }));
  await page.route("**/api/console/agents?**", route =>
    json(route, [
      {
        agentId: "personal-assistant",
        name: "personal-assistant",
        systemPrompt: "Be accurate.",
        modelProvider: "echo",
        modelName: "echo-local",
        workspace: ".harness-agent/workspaces/personal-assistant",
        workloadType: "OFFICE",
        compaction: true,
        maxIters: 3
      }
    ])
  );
  await page.route("**/api/console/agents/*/prompt?**", route =>
    json(route, {
      agentId: "personal-assistant",
      name: "personal-assistant",
      systemPrompt: "Updated.",
      modelProvider: "echo",
      modelName: "echo-local",
      workspace: ".harness-agent/workspaces/personal-assistant",
      workloadType: "OFFICE",
      compaction: true,
      maxIters: 3
    })
  );
  await page.route("**/api/console/agents/*/config?**", route =>
    json(route, {
      agentId: "personal-assistant",
      name: "personal-assistant",
      systemPrompt: "Be accurate.",
      modelProvider: "echo",
      modelName: "echo-local",
      workspace: ".harness-agent/workspaces/personal-assistant",
      workloadType: "OFFICE",
      compaction: true,
      maxIters: 4
    })
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
      ownerScopeId: "personal",
      agentId: "personal-assistant",
      providerId: "echo",
      tokenEvents: 2,
      estimatedTokens: 4200,
      estimatedCost: 0.042
    })
  );
  await page.route("**/api/orchestration/traces?**", route =>
    json(route, [
      {
        id: "trace-1",
        occurredAt: "2026-06-14T00:00:00Z",
        ownerId: "owner-a",
        supervisorAgentId: "supervisor",
        selectedAgentId: "research-agent",
        taskIntent: "research handoff",
        confidence: 0.91,
        status: "COMPLETED",
        candidateAgentIds: ["research-agent", "writer-agent"],
        steps: [{ id: "step-1", agentId: "research-agent", action: "retrieve", input: {}, output: {}, status: "SUCCEEDED" }],
        handoffs: [{ occurredAt: "2026-06-14T00:00:00Z", fromAgentId: "supervisor", toAgentId: "research-agent", reason: "research handoff", sharedContext: {} }],
        attributes: { diagnostic: "ok" }
      }
    ])
  );
}

function plan() {
  return {
    id: "plan-1",
    ownerId: "owner-a",
    agentId: "personal-assistant",
    sessionId: "session-1",
    goal: "Complete workbench",
    steps: ["Collect workspace context", "Implement personal UI", "Verify desktop and mobile"],
    uri: "workspace://plans/plan-1.md",
    createdAt: "2026-06-14T00:00:00Z",
    status: "IN_PROGRESS",
    currentStep: "Collect workspace context",
    blockers: []
  };
}

function workspaceFile(name: string) {
  return {
    uri: `workspace://artifacts/${name}`,
    relativePath: `artifacts/${name}`,
    fileName: name,
    mimeType: "text/markdown",
    size: 32,
    updatedAt: "2026-06-14T00:00:00Z"
  };
}

function knowledgeSource() {
  return {
    id: "source-1",
    ownerScopeId: "personal",
    ownerId: "owner-a",
    agentId: "personal-assistant",
    title: "Runbook",
    version: "v1",
    visibility: "PUBLIC",
    allowedOwnerIds: [],
    updatePolicy: "MANUAL",
    sourceType: "LOCAL_FILE",
    sourceUri: "workspace://artifacts/runbook.md",
    indexStatus: "INDEXED",
    indexedAt: "2026-06-14T00:00:00Z",
    status: "ACTIVE",
    createdAt: "2026-06-14T00:00:00Z",
    updatedAt: "2026-06-14T00:00:00Z"
  };
}

function memory(id: string, title: string) {
  return {
    id,
    ownerScopeId: "personal",
    ownerId: "owner-a",
    agentId: "personal-assistant",
    sessionId: "session-1",
    layer: "FACT_LEDGER",
    title,
    content: "Use concise implementation notes.",
    status: "CONFIRMED",
    sourceId: "source-1",
    createdAt: "2026-06-14T00:00:00Z",
    updatedAt: "2026-06-14T00:00:00Z"
  };
}

function toolDefinition() {
  return {
    id: "tool-2",
    ownerScopeId: "personal",
    name: "search.docs",
    description: "Search personal documents",
    ownerSystem: "workbench",
    ownerId: "owner-a",
    sourceType: "INTERNAL",
    sourceRef: "workbench",
    riskLevel: "READ_ONLY",
    mutating: false,
    enabled: true,
    parameterSchema: { requiredParameters: ["query"], optionalParameters: [], allowedValues: {}, sensitiveParameters: [], workspacePathParameters: [] },
    outputSchema: { outputType: "json", schema: {} },
    permissionPolicy: {
      allowedOwnerIds: ["owner-a"],
      allowedAgentIds: ["personal-assistant"],
      deniedOwnerIds: []
    },
    activityPolicy: { enabled: true, sensitiveParameters: [], sensitiveResultFields: [] },
    workloadType: "OFFICE",
    createdAt: "2026-06-14T00:00:00Z",
    updatedAt: "2026-06-14T00:00:00Z"
  };
}

function personalSkill() {
  return {
    id: "skill-1",
    ownerScopeId: "personal",
    ownerId: "owner-a",
    name: "answer-review",
    description: "Review generated answers",
    version: "1.0.0",
    triggers: ["review"],
    sourceType: "LOCAL",
    source: "answer-review",
    permissions: {
      fileRead: true,
      fileWrite: false,
      toolExecution: true,
      networkAccess: false,
      memoryWrite: false,
      sandbox: false
    },
    resources: ["SKILL.md"],
    agentIds: ["personal-assistant"],
    status: "ENABLED",
    updatedAt: "2026-06-14T00:00:00Z"
  };
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
