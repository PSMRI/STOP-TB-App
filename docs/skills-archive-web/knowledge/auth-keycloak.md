# Keycloak auth (as-built)

**Why:** [ADR-0021](../docs/adr/0021-keycloak-auth.md) · [ADR-0040](../docs/adr/0040-ui-only-repo.md). Cursor skill: [keycloak-auth](../.cursor/skills/keycloak-auth/SKILL.md).

## Surface (client)

| Protected | Public |
|-----------|--------|
| `/factory` UI (auto Keycloak login) | `/` landing, `/items` |
| `/my-projects` | |
| `/factory/design-studio` | designer or admin |

Server route protection and JWT verify live in the **separate API repo**.

**Post-login redirect:** `login()` without an explicit `redirectUri` returns to Landing (`/`). Protected routes pass the current path so deep links (e.g. `/factory?projectId=…`) still resume after SSO. Logout / auth-failure also return to `/`. Helper: `src/auth/loginRedirect.ts`.

## Client roles (phase RBAC)

Roles come from the access token claim  
`resource_access[VITE_KEYCLOAK_CLIENT_ID].roles` (not `realm_access`).

| Client role | Pipeline access |
|-------------|-----------------|
| `admin` | All phases (Ideation → Ship) |
| `analyst` | Discovery only |
| `architect` | Architecture (`solution`) only |
| `designer` | UX & UI (`design`) + Design Studio |
| `factory-admin` / `factory-user` | Legacy full access (ADR-0046) |

- Multiple roles → **union** of phases.
- Soft-bypass (Keycloak triad unset) → treat as admin.
- Authenticated users with **no** Factory client role → blocked at `ProtectedFactoryRoute`.
- Helpers: `src/auth/roles.ts`; `useAuth().roles` / `useAuth().userId` (`JWT sub`, soft-bypass → `default-user`) from `KeycloakProvider`.
- **Rename project:** owner (`owner_id === userId`) or admin only (`canRenameProject`). Shared non-admin assignees cannot rename (API 403 on name PATCH).

API: when `AUTHZ_ENABLED=true`, orchestrate rejects phase calls the caller’s roles cannot access (403).

**Admin project share:** `admin` / `factory-admin` can list assignees via `GET /api/v1/users` (optional `?role=` filter) and `POST /api/v1/projects/{id}/shares` with the chosen user’s Keycloak `sub`. Shared users see the project in My Projects. **Roles stay in Keycloak** — Share Role is an optional filter on the user picker only (ADR-0044 / API ADR-0051), not an assignable field. Phase access remains JWT client roles (legacy `project_shares.role` / `my_share_role` only if already stored).

API user directory (separate repo), in order:
1. Keycloak Admin API using the caller’s Bearer token (needs realm-management `view-users`), or
2. Client credentials via **`KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET`** (dedicated confidential client + service account + `view-users`), or
3. `known_factory_users` cache (usernames of people who have logged into Factory) + labeled share assignees.

**Do not** set `project-factory-client` to confidential — the UI uses public PKCE (`keycloak-js`). A confidential SPA client causes stuck “Checking sign-in…” / refresh loops. Keep the SPA client **public**; create a separate admin client for user listing.

Soft-bypass returns fixture users with usernames.

## API client 401 handling

Shared `src/api/client.ts` attaches Bearer via `getValidAccessToken()`. On **401**:

1. Force-refresh the Keycloak access token (`updateToken(-1)` via `forceRefreshAccessToken`).
2. If a new token is returned → **retry the request once**.
3. If refresh fails / no token, **or the retry is still 401** → **logout** (`handleAuthFailure` → Keycloak `logout` to `/`).

Soft-bypass (Keycloak triad unset): no refresher/logout handlers registered; 401 still surfaces as `ApiError`.

`POST /api/v1/orchestrate` (SSE) uses the same `fetchAuthorized` path via `src/api/orchestrate.ts` — not a raw `fetch`.

## Stack (this repo)

| Layer | Choice |
|-------|--------|
| Web | `keycloak-js` PKCE, `check-sso` (`src/auth/`) |

## Env (UI)

| Var | Role |
|-----|------|
| `VITE_KEYCLOAK_URL` / `VITE_KEYCLOAK_REALM` / `VITE_KEYCLOAK_CLIENT_ID` | Web PKCE |

Soft-bypass identity: `default-user` when the Keycloak triad is incomplete.

Default shared IdP (ops must allow `http://localhost:5173/*`):  
`https://idp-dev3.saal.ai/auth` · realm `project-factory` · client `project-factory-client`.

## Paths

| Concern | Path |
|---------|------|
| Web auth | `src/auth/` |
| Role matrix | `src/auth/roles.ts` |
| API auth / ownership | Separate API repo |

## Rules

- Do not invent local password auth in the UI.
- Do not add FastAPI/JWKS code to this repository.
- Do not invent roles outside the Keycloak client role catalog above.
