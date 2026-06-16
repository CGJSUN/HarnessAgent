import { describe, expect, it } from "vitest";
import { ApiClient, createSseParser, parseApiError } from "./client";
import { DEFAULT_IDENTITY } from "./identity";

describe("parseApiError", () => {
  it("classifies structured conflict authorization failures as access denied", async () => {
    const response = new Response(
      JSON.stringify({
        message: "admin, ops, or auditor role is required",
        timestamp: "2026-06-14T00:00:00Z"
      }),
      { status: 409, headers: { "Content-Type": "application/json" } }
    );

    const error = await parseApiError(response);

    expect(error.status).toBe(409);
    expect(error.message).toContain("role is required");
    expect(error.accessDenied).toBe(true);
  });

  it("falls back to HTTP status text for non JSON errors", async () => {
    const response = new Response("upstream gateway unavailable", {
      status: 502,
      statusText: "Bad Gateway"
    });

    const error = await parseApiError(response);

    expect(error.message).toBe("upstream gateway unavailable");
    expect(error.accessDenied).toBe(false);
  });
});

describe("createSseParser", () => {
  it("parses typed server-sent events across chunks", () => {
    const events: Array<{ type: string; kind?: string; content: string; terminal: boolean }> = [];
    const parser = createSseParser(event => events.push(event));

    parser.feed('event: delta\ndata: {"type":"delta","kind":"TEXT_DELTA","content":"Hel","terminal":false}\n');
    parser.feed('\nevent: done\ndata: {"type":"done","kind":"COMPLETION","content":"","terminal":true}\n\n');
    parser.flush();

    expect(events).toEqual([
      { type: "delta", kind: "TEXT_DELTA", content: "Hel", terminal: false },
      { type: "done", kind: "COMPLETION", content: "", terminal: true }
    ]);
  });

  it("preserves citation and no-answer metadata from stream events", () => {
    const events: Array<{
      type: string;
      content: string;
      terminal: boolean;
      noAnswerReason?: string;
      citations?: unknown[];
    }> = [];
    const parser = createSseParser(event => events.push(event));

    parser.feed(
      'event: done\ndata: {"type":"done","content":"completed","terminal":true,"noAnswerReason":"insufficient evidence","citations":[{"sourceId":"s1","title":"Runbook","version":"v1","chunkIndex":0,"chunkId":"c1"}]}\n\n'
    );

    expect(events[0].noAnswerReason).toBe("insufficient evidence");
    expect(events[0].citations).toHaveLength(1);
  });
});

describe("ApiClient", () => {
  it("uses stable confirmation identifiers for the same pending tool prompt", async () => {
    const bodies: unknown[] = [];
    const client = new ApiClient({
      getIdentity: () => DEFAULT_IDENTITY,
      fetcher: async (_input, init) => {
        bodies.push(JSON.parse(String(init?.body)));
        return new Response(
          JSON.stringify({
            executionId: "execution-1",
            toolId: "tool-1",
            status: "SUCCEEDED",
            message: "ok",
            output: {},
            approvalRequired: false,
            operationSummary: {}
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }
    });
    const prompt = {
      toolId: "tool-1",
      toolName: "finance.transfer",
      sessionId: "session-1",
      sanitizedInput: { amount: "100" },
      operationSummary: { amount: "100" },
      idempotencyKey: "idem-1"
    };

    await client.executeToolConfirmation("agent-a", prompt, true);
    await client.executeToolConfirmation("agent-a", prompt, true);

    expect(bodies).toHaveLength(2);
    expect((bodies[0] as { idempotencyKey: string }).idempotencyKey).toBe("idem-1");
    expect((bodies[1] as { idempotencyKey: string }).idempotencyKey).toBe("idem-1");
    expect((bodies[0] as { approvalId: string }).approvalId).toBe((bodies[1] as { approvalId: string }).approvalId);
  });

  it("serializes audit datetime-local filters as backend Instant strings", async () => {
    let requested = "";
    const client = new ApiClient({
      getIdentity: () => DEFAULT_IDENTITY,
      fetcher: async input => {
        requested = String(input);
        return new Response(JSON.stringify({ toolAudit: [], securityAudit: [] }), {
          status: 200,
          headers: { "Content-Type": "application/json" }
        });
      }
    });

    await client.audit({ from: "2026-06-14T10:30", to: "2026-06-14T11:45" });

    const url = new URL(requested, "http://localhost");
    expect(url.searchParams.get("from")).toMatch(/2026-06-14T.*Z$/);
    expect(url.searchParams.get("to")).toMatch(/2026-06-14T.*Z$/);
  });
});
