import type { LocalIdentity } from "../api/types";
import { TextField } from "../components/common";

export function IdentityPanel({
  identity,
  onIdentityChange
}: {
  identity: LocalIdentity;
  onIdentityChange: (identity: LocalIdentity) => void;
}) {
  return (
    <section className="identity-panel" aria-label="Local identity">
      <TextField
        label="Owner"
        value={identity.ownerId}
        onChange={ownerId => onIdentityChange({ ...identity, ownerId })}
      />
      <TextField
        label="Agent"
        value={identity.agentId}
        onChange={agentId => onIdentityChange({ ...identity, agentId })}
      />
    </section>
  );
}
