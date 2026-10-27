---
name: keycloak-auth
description: >-
  Implement Keycloak SSO for Project Factory (keycloak-js PKCE on web, JWKS
  JWT verify on FastAPI, projects.owner_id scoping). Use when adding or
  changing login, Bearer auth, require_user, protected Factory routes, or
  project ownership — not for Items golden-path public CRUD.
knowledge:
  - knowledge/auth-keycloak.md
  - knowledge/stack.md
  - knowledge/api-conventions.md
  - knowledge/web-conventions.md
  - knowledge/tdd.md
  - knowledge/workflow.md
  - knowledge/execution.md
rules:
  - .cursor/rules/auth-keycloak.mdc
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/web-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

# Keycloak Auth

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — especially [auth-keycloak.md](../../../knowledge/auth-keycloak.md) and [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:`.
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from a resolve-skills matrix via `implement-feature` / `fix-feature` — not as an entry skill. Gate skill [api-auth](../api-auth/SKILL.md) defers here after ADR-0021.

## Pipeline position

```
implement-feature → add-test → keycloak-auth (API + web) → add-migration (owner_id)
```

## Process

1. Configure env triad (`KEYCLOAK_*` / `VITE_KEYCLOAK_*`); soft-bypass when incomplete unless `AUTH_REQUIRED`.
2. API: `api/auth/` — env helpers, JWKS verify, `require_user` dependency.
3. Scope `/api/v1/projects*` by `owner_id = user.id`; set on create. Protect `/ideas/refine`.
4. Web: Keycloak provider + protect `/factory` (auto `login()` on mount); api client attaches Bearer.
5. TDD with mocked verify / bypass — never live IdP in CI.
6. Items CRUD stays public.

## Rules

| Rule | Detail |
|------|--------|
| No password DB | Keycloak only |
| Owner = JWT sub | `projects.owner_id` |
| Soft-bypass | Dev only when triad incomplete |
| Parent only | Not an entry skill |
| Shared playbooks | Obey workflow.md / execution.md when declared |
