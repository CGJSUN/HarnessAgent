# Harness Agent Personal Workbench

Browser workbench for the Spring Boot Harness Agent backend.

The product target is the personal edition: a single owner works with personal Agents, sessions, workspace files, memory/RAG, tools, skills, traces, and local diagnostics. Earlier governed-console concepts are migration history only and should not be treated as the main user journey for new work.

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

Browser tests mock backend API responses and currently cover personal-facing workbench behavior:

- workbench load, session history, RAG citation and no-answer states
- streaming chat send, cancellation, and subsequent recovery
- tool confirmation, knowledge/memory, skill, Agent configuration, and trace diagnostics
- desktop and mobile screenshots in `test-results/`

## Production Hosting

The built assets in `web/dist/` can be served by Spring Boot static resources or by a same-origin gateway. Personal edition identity should come from trusted local or host infrastructure and should resolve to the current owner and Agent.

## Identity Assumptions

The local identity panel is a development helper for personal mode. It stores and sends:

- `ownerId` in request bodies or query strings
- `agentId` for the current personal Agent
- `X-Owner-Id` and `X-Identity-Provider` headers when headers are needed

If trusted owner headers are present, the backend requires request owner values to match those headers. The UI displays backend authorization failures even when they are returned as conflict-style errors instead of `401` or `403`.

## Backend Limitations Reflected In UI

- Message history uses the implemented `GET /api/messages` endpoint.
- Latest citations and uploads in `UserConsoleView` may be empty; the chat view keeps citation rendering ready when backend responses include them.
- Console knowledge revoke is supported, but a console-specific delete action is not exposed.
- Skill enable, disable, upgrade, rollback, lock, refresh, and validation are personal owner operations.
- Tool registration and authorization remain under `/api/tools`; the console view only toggles existing tools.
- `GET /api/orchestration/agents/{agentId}/tool` registers an agent as a tool and is not used for passive UI loading.
- Local development may still use H2, local-json, or in-memory stores. Durable production persistence depends on the production profile and MySQL/JDBC, telemetry, optional Redis, and snapshot wiring.
- New UI work should prioritize chat, plans, workspace files, memory/RAG, tools, skills, Agent configuration, and trace diagnostics. Historical console surfaces belong in explicitly hidden diagnostics or migration tooling.
