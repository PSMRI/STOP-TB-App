# Web conventions (as-built)

How the web app is structured today. **Musts (auto):** `.cursor/rules/web-conventions.mdc`. **Why:** [ADR-0004](../docs/adr/0004-items-golden-path.md) · **UI-only repo:** [ADR-0040](../docs/adr/0040-ui-only-repo.md).

## Structure

- Pages in `src/pages/`
- Register routes in `src/App.tsx`
- Shared HTTP in `src/api/client.ts`
- Per-resource helpers in `src/api/<resource>.ts`
- Project Factory: landing `pages/LandingPage.tsx` at `/`; wizard `pages/FactoryPage.tsx` at `/factory`; `components/factory/` (incl. IdeationForm); helpers in `src/factory/`; projects client `api/projects.ts` → `/api/v1/projects` on the **remote** API (ADR-0017)
- Factory chrome visuals → [factory-ui](../.cursor/skills/factory-ui/SKILL.md) / [factory-ui.md](factory-ui.md) (ADR-0016); Items CRUD → [web-ui](../.cursor/skills/web-ui/SKILL.md)

## Rules

- Pages **never** call `fetch` directly — use `api` / resource helpers
- Types for API payloads live next to the resource client
- Keep UI thin: load → render → mutate → reload
- Env: `VITE_*` from root `.env` (`vite.config.ts` `envDir: "."`; ADR-0040). Docker runtime override via `window.__PF_RUNTIME__` ([ADR-0041](../docs/adr/0041-runtime-config-docker-spa.md)). See `.env.example`.
- Client state: follow [web-state](../.cursor/skills/web-state/SKILL.md) (Items local state)
- Markup/styles: follow [web-ui](../.cursor/skills/web-ui/SKILL.md)
- **TDD:** new client/page behavior → Vitest under `src/**/*.test.ts` ([tdd.md](tdd.md), `make test`)
- Backend / FastAPI / migrations → **separate API repo** (do not add `apps/api` here)

## New page checklist

1. Resource client (`src/api/<resource>.ts`) if talking to API
2. **RED** Vitest for the client (clone `api/items.test.ts`) when behavior is new
3. Page component (`src/pages/<Name>Page.tsx`)
4. `<Route>` in `App.tsx`
5. Link from Home or navigation when it is a primary surface

## Golden path reference

See `ItemsPage` + `api/items.ts` + `api/items.test.ts` for list/create/delete.
