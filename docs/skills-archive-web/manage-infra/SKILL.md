---
name: manage-infra
description: >-
  Change Docker Compose, Make targets, CI, env examples, or local run wiring
  for this monorepo. Use when editing infra/, Makefile, .github/, .env.example,
  or ports/services setup.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Manage Infra

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — [workflow.md](../../../knowledge/workflow.md) + [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Process

1. Read `knowledge/stack.md` for ports/commands (Postgres **5433**, api `:8000`, web `:5173`).
2. **Conflict check (Hard stop):** if Compose/Make/README contradict `knowledge/stack.md` (or other matrix contracts) on the same must → raise a `Contract conflict` table → [update-docs](../update-docs/SKILL.md) first; do **not** pick a side silently ([execution.md](../../../knowledge/execution.md)).
3. Change only files in scope; keep `.env.example` in sync when adding env vars.
4. Prefer existing Make targets; document new ones in root `README.md` via [update-docs](../update-docs/SKILL.md) if needed.
5. Lasting infra choice (e.g. new service) → [write-adr](../write-adr/SKILL.md).
6. Finish with [review-work](../review-work/SKILL.md) → [verify-feature](../verify-feature/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| Stack ports | Do not silently change published ports |
| No secrets | Never commit `.env` |
| Contract conflict | Hard stop — raise table; fix docs before edits |
| Shared workflow | Obey `knowledge/workflow.md` |
| Shared execute | Obey `knowledge/execution.md` |
| Docs | Update README/knowledge when commands change |
