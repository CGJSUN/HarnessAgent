# Enterprise Term Guardrail

This document tracks the executable inventory for removing enterprise-era concepts from the personal Agent product path.

The guardrail is intentionally strict for personal main paths. It allows only explicitly documented migration, archive, legacy, and database-migration contexts listed in `scripts/enterprise-term-guardrail.config.json`.

## Commands

Inventory mode prints current residual terms and exits successfully:

```bash
node scripts/validate-personal-terms.mjs --inventory
```

Gate mode fails when unallowed terms are present:

```bash
node scripts/validate-personal-terms.mjs
```

JSON output is available for tooling:

```bash
node scripts/validate-personal-terms.mjs --json
```

## Current Inventory

Initial scan date: 2026-06-23.

| Category | Matches |
|---|---:|
| domain-service | 574 |
| persistence | 497 |
| docs | 327 |
| public-api | 284 |
| tests | 284 |
| frontend | 82 |
| other | 11 |

| Term | Matches |
|---|---:|
| tenant | 1370 |
| role | 176 |
| audit | 173 |
| department | 90 |
| admin | 90 |
| enterprise | 73 |
| ops | 41 |
| release-gate | 27 |
| rbac | 19 |

Total matches: 2059.
Allowed matches: 284.
Blocked matches: 1775.

## Cleanup Use

- Use `--inventory` to choose the next cleanup slice.
- Keep allowlist entries narrow and reasoned. Do not add product-path files to the allowlist to make the gate pass.
- Run gate mode after each cleanup slice. The final release task requires zero unallowed matches in personal main paths.
- Migration SQL, archive material, explicit legacy adapters, and historical migration notes may keep enterprise terms only when the allowlist records the reason.
