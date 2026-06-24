import type { LocalIdentity } from "./types";

export const DEFAULT_IDENTITY: LocalIdentity = {
  ownerId: "owner-a",
  agentId: "personal-assistant",
  identityProvider: "INTERNAL"
};

export function identitySearchParams(identity: LocalIdentity): URLSearchParams {
  return new URLSearchParams({
    ownerId: identity.ownerId
  });
}

export function identityPayload(identity: LocalIdentity) {
  return {
    ownerId: identity.ownerId
  };
}

export function identityHeaders(identity: LocalIdentity): Record<string, string> {
  return {
    "X-Owner-Id": identity.ownerId,
    "X-Identity-Provider": identity.identityProvider
  };
}
