---
name: add-api-endpoint
description: >-
  Add a FastAPI resource (model, schemas, routes) under apps/api following the
  Items golden path. Use when adding an API endpoint, CRUD resource backend,
  or FastAPI route.
knowledge:
  - knowledge/api-conventions.md
  - knowledge/stack.md
  - knowledge/tdd.md
rules:
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

**Out of tree (ADR-0040):** This skill targets the separate Project Factory **API** repository. Do not recreate `apps/api` in this UI repo — mark the concern N/A here and implement there.

# Add API Endpoint

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:`.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from a resolve-skills matrix via `implement-feature` / `fix-feature` / `refactor-code` — not as an entry skill. API behavior changes require TDD via [add-test](../add-test/SKILL.md) first ([tdd.md](../../../knowledge/tdd.md)).

## Process

1. Read `knowledge/api-conventions.md` and mirror `items`.
2. Add model in `apps/api/src/api/models/<name>.py`; export in `models/__init__.py`.
3. Add schemas in `apps/api/src/api/schemas/<name>.py` (`Create` / `Update` / `Read`).
4. Add router in `apps/api/src/api/routes/<name>.py` with list/get/create/patch/delete.
5. Include router in `apps/api/src/api/routes/__init__.py` on `api_router`.
6. Run [add-migration](../add-migration/SKILL.md) if the model is new or columns changed.
7. Verify via OpenAPI at `/docs` or curl `/api/v1/<plural>`.

## Patterns to copy

- `apps/api/src/api/models/item.py`
- `apps/api/src/api/schemas/item.py`
- `apps/api/src/api/routes/items.py`

## Rules

| Rule | Detail |
|------|--------|
| `/api/v1` only | No ad-hoc mounts on `main.py` |
| 404 detail | `"<Resource> not found"` |
| Delete | 204 empty body |
| Auth | If needed → [api-auth](../api-auth/SKILL.md); never invent here |
| Beyond CRUD | [api-patterns](../api-patterns/SKILL.md) |
