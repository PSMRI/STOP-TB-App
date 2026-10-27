# Stack (as-built)

Load this before implementing any feature. Shared playbooks: [workflow.md](workflow.md) · [execution.md](execution.md) · [tdd.md](tdd.md).

**Why this stack:** [ADR-0003](../docs/adr/0003-vite-fastapi-postgres-stack.md) (historical monorepo) · **UI-only split:** [ADR-0040](../docs/adr/0040-ui-only-repo.md) · **Skills-first:** [ADR-0002](../docs/adr/0002-skills-first-development.md) · **TDD:** [ADR-0015](../docs/adr/0015-tdd-based-development.md) · **ADRs index:** [docs/adr/](../docs/adr/README.md)

## Versions / tools

| Piece | Choice |
|-------|--------|
| Web | React 19 + Vite 6 + TypeScript 5.8 + React Router 7 |
| Backend | **Separate** Project Factory API — not in this repo (ADR-0040) |
| Auth (client) | Keycloak PKCE (`src/auth/`; ADR-0021) |
| Containers | Root `Dockerfile` (Vite build → nginx SPA); `GET /health` + image/`compose` healthcheck |
| Web tests | Vitest + jsdom (`src/**/*.test.ts`) |
| Package manager | npm |

## Commands

```bash
cp .env.example .env   # once — set VITE_API_URL to the API host
make install
make web               # :5173
make test
make build
# Optional Docker SPA:
make up
make docker-build
```

## Layout map

| Concern | Path |
|---------|------|
| Web entry | `index.html`, `src/main.tsx`, `src/App.tsx` |
| Global CSS | `src/index.css` |
| Auth | `src/auth/` (Keycloak; ADR-0021) |
| API client | `src/api/client.ts` |
| Resource clients | `src/api/<resource>.ts` |
| Pages | `src/pages/` |
| Factory chrome | `src/factory/`, `src/components/factory/` |
| Web tests | `src/**/*.test.ts` |
| Env | Root `.env` / `.env.example` (`VITE_*`); Docker also accepts the same vars at **runtime** ([ADR-0041](../docs/adr/0041-runtime-config-docker-spa.md)) |

## Golden path

**Items** remains the reference *client* slice (list/create/delete via remote API). Clone it for new resource UIs; do not invent a second style. Tests: `src/api/items.test.ts`.

**Why:** [ADR-0004](../docs/adr/0004-items-golden-path.md)

## Definition of done (UI slice)

1. Resource client uses `api/client` (no raw `fetch` in pages)
2. Page + route registered
3. **Web tests green** (`make test`) — TDD-first for new client/page behavior
4. Matches `web-conventions.md` and `tdd.md`
5. Talks to the separate API via `VITE_API_URL` — server changes belong in the API repo
