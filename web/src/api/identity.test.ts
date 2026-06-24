import { describe, expect, it } from "vitest";
import {
  DEFAULT_IDENTITY,
  identityHeaders,
  identityPayload,
  identitySearchParams
} from "./identity";

describe("identity serialization", () => {
  it("serializes personal owner identity into trusted headers and request payload fields", () => {
    const identity = {
      ...DEFAULT_IDENTITY,
      ownerId: "owner-a",
      agentId: "personal-agent"
    };

    expect(identitySearchParams(identity).toString()).toBe("ownerId=owner-a");
    expect(identityPayload(identity)).toEqual({
      ownerId: "owner-a"
    });
    expect(identityHeaders(identity)).toMatchObject({
      "X-Owner-Id": "owner-a",
      "X-Identity-Provider": "INTERNAL"
    });
    expect(Object.keys(identityHeaders(identity)).sort()).toEqual([
      "X-Identity-Provider",
      "X-Owner-Id"
    ]);
  });
});
