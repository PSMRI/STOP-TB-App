---
name: api-patterns
description: >-
  Apply non-trivial FastAPI patterns beyond plain Items CRUD (dependencies,
  multi-model flows, internal helpers) while staying consistent with repo
  layering. Use when API work is more than Clone-Items CRUD or when tempted
  to put logic in main.py / routes incorrectly.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/api-conventions.md
  - knowledge/stack.md
rules:
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/skills-first.mdc
---

**Out of tree (ADR-0040):** This skill targets the separate Project Factory **API** repository. Do not recreate `apps/api` in this UI repo — mark the concern N/A here and implement there.
# API Patterns

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — [workflow.md](../../../knowledge/workflow.md) + [execution.md](../../../knowledge/execution.md) when closing a slice alone.
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

**Parent only:** prefer matrix via `implement-feature` / `fix-feature` — not as a freestyle entry skill.

## Process

1. Default: plain CRUD → [add-api-endpoint](../add-api-endpoint/SKILL.md) only.
2. If you need shared logic: keep routes thin; put helpers next to the domain (not in `main.py`). Still: routes → schemas → models.
3. New cross-cutting pattern (caching, background jobs, etc.) → **stop** → [write-adr](../write-adr/SKILL.md) + [find-skills](../find-skills/SKILL.md) / [write-a-skill](../write-a-skill/SKILL.md).
4. Auth remains [api-auth](../api-auth/SKILL.md).
5. Follow [knowledge/execution.md](../../../knowledge/execution.md) for conflict check / review→verify when finishing.

## Rules

| Rule | Detail |
|------|--------|
| Layering holds | No business logic in `main.py` |
| Prefer CRUD skill | Don’t over-abstract Items-like resources |
| Shared workflow | Obey `knowledge/workflow.md` |
| Shared execute | Obey `knowledge/execution.md` |
| ADR for new patterns | Don’t silently invent architecture |
