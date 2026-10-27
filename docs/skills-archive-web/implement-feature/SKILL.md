---
name: implement-feature
description: >-
  Implement a planned vertical slice using knowledge conventions and stack
  skills. Use when the user says implement feature, build this, ship the plan,
  or execute after plan-feature / grill-me.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/tdd.md
  - knowledge/stack.md
  - knowledge/api-conventions.md
  - knowledge/web-conventions.md
rules:
  - .cursor/rules/skills-first.mdc
  - .cursor/rules/api-conventions.mdc
  - .cursor/rules/web-conventions.mdc
---

# Implement Feature

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — [workflow.md](../../../knowledge/workflow.md) + [execution.md](../../../knowledge/execution.md).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

## Process

### 1. Matrix + gates

Require a needed-vs-have matrix from [resolve-skills](../resolve-skills/SKILL.md) (or the plan that embeds it). If missing or stale → run resolve-skills first.  
If product gates are not frozen → [grill-me](../grill-me/SKILL.md).

### 2. Shared execute contract

Follow [knowledge/execution.md](../../../knowledge/execution.md) (conflict check → **TDD** via [tdd.md](../../../knowledge/tdd.md) / [add-test](../add-test/SKILL.md) → matrix skills → review→verify).

### 3. Execute only matrix skills

Follow HAVE skills in order (typically **add-test RED** then stack + craft). Examples:

| Concern | Skill |
|---------|-------|
| Tests (API TDD) | [add-test](../add-test/SKILL.md) |
| Full CRUD | [add-crud-resource](../add-crud-resource/SKILL.md) |
| State / UI | [web-state](../web-state/SKILL.md) / [web-ui](../web-ui/SKILL.md) |
| Auth | [api-auth](../api-auth/SKILL.md) |
| API beyond CRUD | [api-patterns](../api-patterns/SKILL.md) |

### 4. Mid-work re-resolve

New concern → [resolve-skills](../resolve-skills/SKILL.md) before more app code. Do not absorb craft into CRUD skills.

## Rules

| Rule | Detail |
|------|--------|
| Shared workflow | Obey `knowledge/workflow.md` |
| Shared execute | Obey `knowledge/execution.md` — do not fork those musts here |
| Skills only | Only HAVE skills |
