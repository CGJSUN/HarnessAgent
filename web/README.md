# Harness Agent Web Console

Browser console for the Spring Boot Harness Agent backend.

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

Browser tests mock backend API responses and cover:

- workbench load, session history, RAG citation and no-answer states
- streaming chat send, cancellation, and subsequent recovery
- one admin view and one operations/audit workflow
- desktop and mobile screenshots in `test-results/`

## Production Hosting

The built assets in `web/dist/` can be served by Spring Boot static resources or by an enterprise gateway on the same origin as the backend API. Production identity and role headers must be injected by trusted infrastructure, not by arbitrary browser code.

## Identity Assumptions

The local identity panel sends tenant, user, roles, and departments through both the backend request contract and trusted-header shape:

- `tenantId`, `userId`, `roles`, `departments` in JSON bodies or query strings
- `X-Tenant-Id`, `X-User-Id`, `X-Roles`, `X-Departments`, `X-Identity-Provider` headers

If trusted headers are present, the backend requires request tenant/user values to match those headers. The UI displays backend authorization failures even when they are returned as conflict-style errors instead of `401` or `403`.

## Backend Limitations Reflected In UI

- Message history uses the implemented `GET /api/messages` endpoint.
- Latest citations and uploads in `UserConsoleView` may be empty; the chat view keeps citation rendering ready when backend responses include them.
- Console knowledge revoke is supported, but a console-specific delete action is not exposed.
- Skill approve, publish, and disable are supported; Skill rollback is shown as a disabled future action because no REST endpoint exposes it.
- Tool registration and authorization remain under `/api/tools`; the console view only toggles existing tools.
- `GET /api/orchestration/agents/{agentId}/tool` registers an agent as a tool and is not used for passive UI loading.
- Local development may still use H2, local-json, or in-memory stores. Durable production persistence depends on the production profile and MySQL/JDBC, telemetry, optional Redis, and snapshot wiring.
