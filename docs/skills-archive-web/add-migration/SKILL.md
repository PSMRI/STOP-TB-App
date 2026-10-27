---
name: add-migration
description: >-
  Create and apply an Alembic migration for SQLAlchemy model changes in
  apps/api. Use when adding/changing models, schema drift, or the user says
  add migration, alembic revision, or migrate database.
knowledge:
  - knowledge/api-conventions.md
  - knowledge/stack.md
rules:
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

**Out of tree (ADR-0040):** This skill targets the separate Project Factory **API** repository. Do not recreate `apps/api` in this UI repo — mark the concern N/A here and implement there.
# Add Migration

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:`.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** run from a resolve-skills matrix via `implement-feature` / `fix-feature` / `refactor-code` — not as an entry skill.

## Process

1. Ensure models are updated and imported in `apps/api/src/api/models/__init__.py`.
2. From repo root:

```bash
make migrate-rev m="short message"
make migrate
```

3. Review the generated file under `apps/api/alembic/versions/`.
4. If autogenerate missed something, edit the revision before applying.
5. Confirm upgrade succeeds against local Postgres (`make up` first).

## Checklist

- [ ] Model registered for autogenerate
- [ ] Revision message is clear
- [ ] `upgrade` / `downgrade` both sensible
- [ ] `make migrate` applied

## Reference

Mirror `apps/api/alembic/versions/0001_items.py`. See `knowledge/api-conventions.md`.
