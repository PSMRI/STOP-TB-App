---
name: update-docs
description: >-
  Update knowledge/, ADRs index links, README, or Cursor rules text to match
  as-built reality. Use when documentation or rules drift, or after a feature
  changes how-to / musts (not for app feature code itself).
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Update Docs

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — [workflow.md](../../../knowledge/workflow.md) + [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Process

1. Decide target: `knowledge/` (how), `.cursor/rules/` (musts), `docs/adr/` (why — use [write-adr](../write-adr/SKILL.md) for new decisions), or root `README.md`.
2. Keep knowledge and scoped rules aligned (rules = short musts; knowledge = detail). Shared **workflow** musts → [workflow.md](../../../knowledge/workflow.md) only; shared **execute** musts → [execution.md](../../../knowledge/execution.md) only. Keep `skills-first.mdc` as a thin pointer.
3. When a **new/updated skill** adds domain behavior: wire frontmatter `knowledge:` / `rules:`. Workflow skills → `workflow.md`; executors → `execution.md`.
4. Do not put long checklists only in always-apply rules.
5. If skills catalog/scenarios changed, update `.cursor/skills/README.md` and run `make validate-skills-strict`.
6. Follow [execution.md](../../../knowledge/execution.md) for review→verify when closing the change.

## Rules

| Rule | Detail |
|------|--------|
| How vs why | knowledge ≠ ADR |
| Single playbooks | Workflow → `workflow.md`; execute → `execution.md` |
| No silent drift | Code change that alters how-to should update knowledge/rules |
| Validate | `make validate-skills` when skills/docs refs change |
