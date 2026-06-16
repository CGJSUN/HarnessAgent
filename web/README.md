# Harness Agent Personal Workbench

Browser workbench for the Spring Boot Harness Agent backend.

The product target is now the personal edition: a single owner works with personal Agents, sessions, workspace files, memory/RAG, tools, skills, traces, and local diagnostics. The earlier enterprise console concepts, including tenant switching, RBAC roles, admin/ops views, audit reporting, and release gates, are legacy compatibility or developer diagnostics. They should not be treated as the main user journey for new work.

## Local Development

Start the backend from the repository root:

```bash
mvn spring-boot:run
```

Start the web console from `web/`:

```bash
npm install
npm run dev
```

The Vite dev server proxies `/api/**` to `http://localhost:8080`, so local browser calls stay same-origin and do not require broad CORS rules.

## Verification

```bash
npm run test:unit
npm run test:browser
npm run build
```

Browser tests mock backend API responses and currently cover both personal-facing workbench behavior and legacy console compatibility:

- workbench load, session history, RAG citation and no-answer states
- streaming chat send, cancellation, and subsequent recovery
- one legacy admin view and one legacy operations/audit workflow
- desktop and mobile screenshots in `test-results/`

## Production Hosting

The built assets in `web/dist/` can be served by Spring Boot static resources or by a same-origin gateway. Personal edition identity should come from trusted local or host infrastructure. Legacy production identity and role headers, when enabled for compatibility, must be injected by trusted infrastructure and not by arbitrary browser code.

## Identity Assumptions

The current local identity panel is a legacy development helper. It sends tenant, user, roles, and departments through both the backend request contract and trusted-header shape:

- `tenantId`, `userId`, `roles`, `departments` in JSON bodies or query strings
- `X-Tenant-Id`, `X-User-Id`, `X-Roles`, `X-Departments`, `X-Identity-Provider` headers

If trusted headers are present, the backend requires request tenant/user values to match those headers. In personal mode, tenant should be treated as the compatibility scope `personal`, user should be treated as the owner, and roles/departments should not be required for the primary workflow. The UI displays backend authorization failures even when they are returned as conflict-style errors instead of `401` or `403`.

## Backend Limitations Reflected In UI

- Message history uses the implemented `GET /api/messages` endpoint.
- Latest citations and uploads in `UserConsoleView` may be empty; the chat view keeps citation rendering ready when backend responses include them.
- Console knowledge revoke is supported, but a console-specific delete action is not exposed.
- Skill approve, publish, and disable are supported; Skill rollback is shown as a disabled future action because no REST endpoint exposes it.
- Tool registration and authorization remain under `/api/tools`; the console view only toggles existing tools.
- `GET /api/orchestration/agents/{agentId}/tool` registers an agent as a tool and is not used for passive UI loading.
- Local development may still use H2, local-json, or in-memory stores. Durable production persistence depends on the production profile and MySQL/JDBC, telemetry, optional Redis, and snapshot wiring.
- Personal workbench IA is not complete yet. New UI work should prioritize chat, plans, workspace files, memory/RAG, tools, skills, Agent configuration, and trace diagnostics before expanding legacy admin/ops/release screens.
