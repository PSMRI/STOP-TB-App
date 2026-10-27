---
name: api-auth
description: >-
  Gate skill for authentication and authorization work on the FastAPI app.
  Use when a plan needs login, signup, sessions, JWT, passwords, roles, or
  protected routes — defers to keycloak-auth after ADR-0021 for Factory SSO.
knowledge:
  - knowledge/api-conventions.md
  - knowledge/auth-keycloak.md
  - knowledge/stack.md
rules:
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/auth-keycloak.mdc
  - .cursor/rules/skills-first.mdc
---

# API Auth (gate)

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:`.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Pipeline position

```
plan needs auth → api-auth → keycloak-auth (ADR-0021) → implement
                 ↘ no auth in plan → skip
```

## Process

1. If the plan does **not** include auth → skip this skill.
2. Factory / Keycloak / project ownership → hand off to [keycloak-auth](../keycloak-auth/SKILL.md) (ADR-0021).
3. If a **new** auth approach (not Keycloak) is proposed and no ADR exists:
   - **Hard stop** — do not invent password hashing, alternate JWT, or middleware ad hoc.
   - Run [write-adr](../write-adr/SKILL.md) + [write-a-skill](../write-a-skill/SKILL.md) / [find-skills](../find-skills/SKILL.md).
4. Never invent auth inside [add-api-endpoint](../add-api-endpoint/SKILL.md).

## Current repo fact

Keycloak SSO + `projects.owner_id` is the accepted path ([ADR-0021](../../../docs/adr/0021-keycloak-auth.md)). Items CRUD stays public.

## Rules

| Rule | Detail |
|------|--------|
| No invent | Never invent auth inside CRUD skills |
| Keycloak path | Prefer [keycloak-auth](../keycloak-auth/SKILL.md) |
| ADR first | New lasting auth choice must be recorded |
| Hard stop | Block unknown auth stacks until ADR + skill |
