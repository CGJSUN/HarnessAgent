import type { LocalIdentity } from "./types";

export const DEFAULT_IDENTITY: LocalIdentity = {
  tenantId: "tenant-a",
  userId: "admin-a",
  roles: ["admin", "ops", "auditor"],
  departments: ["support"],
  identityProvider: "INTERNAL"
};

export function identitySearchParams(identity: LocalIdentity): URLSearchParams {
  return new URLSearchParams({
    tenantId: identity.tenantId,
    userId: identity.userId
  });
}

export function identityPayload(identity: LocalIdentity) {
  return {
    tenantId: identity.tenantId,
    userId: identity.userId,
    roles: identity.roles,
    departments: identity.departments
  };
}

export function identityHeaders(identity: LocalIdentity): Record<string, string> {
  return {
    "X-Tenant-Id": identity.tenantId,
    "X-User-Id": identity.userId,
    "X-Roles": identity.roles.join(","),
    "X-Departments": identity.departments.join(","),
    "X-Identity-Provider": identity.identityProvider
  };
}

export function parseCsv(value: string): string[] {
  return value
    .split(",")
    .map(item => item.trim())
    .filter(Boolean);
}
