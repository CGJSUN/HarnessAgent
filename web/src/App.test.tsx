import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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

    expect(screen.getByRole("heading", { name: "Agent workspace" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Message" })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("status")).not.toBeInTheDocument());
  });

  it("keeps admin navigation unavailable when local identity lacks the admin role", async () => {
    render(<App />);

    const roles = screen.getByLabelText("Roles");
    fireEvent.change(roles, { target: { value: "employee" } });
    await waitFor(() => expect(screen.queryByRole("status")).not.toBeInTheDocument());

    const navigation = screen.getByRole("navigation", { name: "Workspace" });
    expect(within(navigation).getByRole("button", { name: "Admin" })).toBeDisabled();
  });
});

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  });
}
