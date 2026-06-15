import { describe, expect, it } from "vitest";
import {
  DEFAULT_IDENTITY,
  identityHeaders,
  identityPayload,
  identitySearchParams
} from "./identity";

describe("identity serialization", () => {
  it("serializes local development identity into trusted headers and request payload fields", () => {
    const identity = {
      ...DEFAULT_IDENTITY,
      tenantId: "tenant-a",
      userId: "admin-a",
      roles: ["admin", "ops"],
      departments: ["support"]
    };

    expect(identitySearchParams(identity).toString()).toBe("tenantId=tenant-a&userId=admin-a");
    expect(identityPayload(identity)).toEqual({
      tenantId: "tenant-a",
      userId: "admin-a",
      roles: ["admin", "ops"],
      departments: ["support"]
    });
    expect(identityHeaders(identity)).toMatchObject({
      "X-Tenant-Id": "tenant-a",
      "X-User-Id": "admin-a",
      "X-Roles": "admin,ops",
      "X-Departments": "support",
      "X-Identity-Provider": "INTERNAL"
    });
  });
});
