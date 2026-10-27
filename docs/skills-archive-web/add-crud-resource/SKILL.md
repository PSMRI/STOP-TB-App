---
name: add-crud-resource
description: >-
  Add a full vertical-slice CRUD resource across Postgres, FastAPI, and Vite
  by composing migration, API, and page skills. Use when adding a new
  resource, entity, or end-to-end CRUD feature.
knowledge:
  - knowledge/stack.md
  - knowledge/api-conventions.md
  - knowledge/web-conventions.md
  - knowledge/tdd.md
rules:
  - .cursor/rules/skills-first.mdc
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/web-conventions.mdc
---

**Out of tree (ADR-0040):** This skill targets the separate Project Factory **API** repository. Do not recreate `apps/api` in this UI repo — mark the concern N/A here and implement there.
# Add CRUD Resource

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:`.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from `implement-feature` (after resolve-skills) — not as an entry skill. Follow [tdd.md](../../../knowledge/tdd.md): [add-test](../add-test/SKILL.md) RED before API production code.

## Process

Compose in order:

0. [add-test](../add-test/SKILL.md) — RED API tests for the resource (TDD)
1. [add-api-endpoint](../add-api-endpoint/SKILL.md) — model, schemas, routes (GREEN)
2. [add-migration](../add-migration/SKILL.md) — revision + upgrade
3. [add-page](../add-page/SKILL.md) — resource client + page shell + route
4. [web-state](../web-state/SKILL.md) — page-local state pattern
5. [web-ui](../web-ui/SKILL.md) — Items/`index.css` UI

If the plan includes auth, run [api-auth](../api-auth/SKILL.md) **before** freestyling protection (may hard-stop).

Use the **Items** golden path as the template for names, fields shape, and UX.

## Suggested naming

| Layer | Pattern | Example (Notes) |
|-------|---------|-----------------|
| Model | `Note` | `models/note.py` |
| Table | `notes` | |
| Routes | `/api/v1/notes` | `routes/notes.py` |
| Page | `NotesPage` | `/notes` |

## DoD

- [ ] `make test-api` green (TDD)
- [ ] `make migrate` clean
- [ ] CRUD works in `/docs`
- [ ] Web list/create/delete (or agreed subset) works
- [ ] Conventions in `knowledge/` followed

## Rules

| Rule | Detail |
|------|--------|
| Compose skills | Do not skip migration, craft skills, or invent layout |
| Thin UI | Match Items simplicity unless plan says otherwise |
| No auth in v1 | Unless plan explicitly adds it — then [api-auth](../api-auth/SKILL.md) |
