import { render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("App shell", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.startsWith("/api/sessions")) {
          return Promise.resolve(jsonResponse([]));
        }
        if (url.startsWith("/api/messages")) {
          return Promise.resolve(jsonResponse([]));
        }
        if (url.startsWith("/api/console/user")) {
          return Promise.resolve(
            jsonResponse({
              sessions: [],
              messages: [],
              latestCitations: [],
              toolStatus: [],
              confirmationPrompts: [],
              fileUploads: []
            })
          );
        }
        return Promise.resolve(jsonResponse({}));
      })
    );
  });

  it("opens on the chat workspace instead of a landing page", async () => {
    render(<App />);

    expect(screen.getByText("Personal Agent Workbench")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Agent workspace" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Message" })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("status")).not.toBeInTheDocument());
  });

  it("keeps personal workbench navigation focused on owner identity", async () => {
    render(<App />);

    await waitFor(() => expect(screen.queryByRole("status")).not.toBeInTheDocument());

    const identityPanel = screen.getByRole("region", { name: "Local identity" });
    expect(within(identityPanel).getByLabelText("Owner")).toBeInTheDocument();
    expect(within(identityPanel).getByLabelText("Agent")).toBeInTheDocument();
    const navigation = screen.getByRole("navigation", { name: "Workspace" });
    expect(within(navigation).getByRole("button", { name: "Tasks" })).toBeEnabled();
    expect(within(navigation).getByRole("button", { name: "Files" })).toBeEnabled();
    expect(within(navigation).getByRole("button", { name: "Knowledge" })).toBeEnabled();
    expect(within(navigation).getByRole("button", { name: "Tools" })).toBeEnabled();
    expect(within(navigation).getByRole("button", { name: "Agent" })).toBeEnabled();
    expect(within(navigation).getByRole("button", { name: "Trace" })).toBeEnabled();
  });
});

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  });
}
