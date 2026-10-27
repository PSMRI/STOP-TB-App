---
name: plan-feature
description: >-
  Scope a product feature, propose file touch list, emit grill-me gates, and
  write a plan. Use when resolve-skills has produced a matrix and route-task
  requires planning, or the user says plan feature, scope this, design this
  feature, or wants a plan before implementation.
knowledge:
  - knowledge/workflow.md
  - knowledge/stack.md
  - knowledge/api-conventions.md
  - knowledge/web-conventions.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Plan Feature

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — especially [workflow.md](../../../knowledge/workflow.md) §3.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Process

1. Require matrix from [resolve-skills](../resolve-skills/SKILL.md) — **do not plan without it**.
2. Load stack + api/web knowledge; skim Items if CRUD.
3. Draft scope only (no code): goal, in/out, file touch list, DoD vs `knowledge/stack.md`.
4. Paste matrix into plan; new concern → re-enter resolve-skills.
5. Gates table → [grill-me](../grill-me/SKILL.md) (one question at a time).
6. Write `plans/YYYY-MM-DD-<slug>.md` (frozen decisions, touch list, order, **full matrix**).
7. Lasting trade-off → [write-adr](../write-adr/SKILL.md).

**Do not write application code.** Pipeline position: [workflow.md](../../../knowledge/workflow.md).

## Rules

| Rule | Detail |
|------|--------|
| Shared workflow | Obey `knowledge/workflow.md` |
| No code | Planning only |
| Durable plan | Always under `plans/` |
| Clone Items | CRUD mirrors Items |
