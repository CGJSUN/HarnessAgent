import type { LocalIdentity } from "../api/types";
import { parseCsv } from "../api/identity";
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
        label="Tenant"
        value={identity.tenantId}
        onChange={tenantId => onIdentityChange({ ...identity, tenantId })}
      />
      <TextField label="User" value={identity.userId} onChange={userId => onIdentityChange({ ...identity, userId })} />
      <TextField
        label="Roles"
        value={identity.roles.join(",")}
        onChange={roles => onIdentityChange({ ...identity, roles: parseCsv(roles) })}
      />
      <TextField
        label="Departments"
        value={identity.departments.join(",")}
        onChange={departments => onIdentityChange({ ...identity, departments: parseCsv(departments) })}
      />
    </section>
  );
}
