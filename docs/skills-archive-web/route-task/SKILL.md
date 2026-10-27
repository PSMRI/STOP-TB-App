---
name: route-task
description: >-
  Entry router for mutating work: classify the request, then hand off to
  resolve-skills before any plan or execution. Use when the user asks to build,
  change, fix, add, refactor, or ship anything that edits the repo — load this
  before coding.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
rules:
  - .cursor/rules/skills-first.mdc
---

# Route Task

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — especially [workflow.md](../../../knowledge/workflow.md) (pipeline + classify table).
Obey frontmatter `rules:` (scoped rules also auto-apply in Cursor).
If this skill changes musts/how-to → [update-docs](../update-docs/SKILL.md); lasting why → [write-adr](../write-adr/SKILL.md).

Mandatory entrypoint for any prompt that will change the repo.

## Process

1. **Classify** using the table in [workflow.md §1](../../../knowledge/workflow.md). Announce kind. Do **not** write application code.
2. Mutating → hand off to [resolve-skills](../resolve-skills/SKILL.md) (mandatory). Do not plan/execute until the matrix is complete.
3. Plan when workflow.md says so → [plan-feature](../plan-feature/SKILL.md) with the matrix.
4. Execute → follow [execution.md](../../../knowledge/execution.md) via the matrix’s executor skill.
5. Mid-work new concern → [resolve-skills](../resolve-skills/SKILL.md) again.

## Rules

| Rule | Detail |
|------|--------|
| Shared workflow | Obey `knowledge/workflow.md` — do not fork pipeline/mandate here |
| Classify only | Skill matching lives in `resolve-skills` |
