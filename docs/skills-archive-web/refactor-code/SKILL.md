---
name: refactor-code
description: >-
  Safely refactor existing code without changing intended behavior, using
  minimal diffs and stack conventions. Use when the user asks to refactor,
  clean up, rename across layers, or restructure without a new product feature.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
  - knowledge/api-conventions.md
  - knowledge/web-conventions.md
rules:
  - .cursor/rules/skills-first.mdc
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/web-conventions.mdc
---

# Refactor Code

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — [workflow.md](../../../knowledge/workflow.md) + [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Process

1. Confirm scope (paths) and “no behavior change” unless the plan says otherwise.
2. Skill-resolve any structural concerns (API/web/migration) before editing.
3. Follow [knowledge/execution.md](../../../knowledge/execution.md) (conflict check → edits → review→verify).
4. Prefer small steps; match Items patterns; no drive-by features.
5. If refactor implies a lasting architecture change → [write-adr](../write-adr/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| Minimal diff | No unrelated cleanup |
| Skills for structure | Use stack/craft skills when moving layers |
| Shared workflow | Obey `knowledge/workflow.md` |
| Shared execute | Obey `knowledge/execution.md` |
