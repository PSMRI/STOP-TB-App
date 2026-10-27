# Write a Skill — reference

## SKILL.md template

```md
---
name: skill-name
description: Brief description of capability. Use when [specific triggers].
knowledge:
  - knowledge/workflow.md          # if workflow / orchestration / executor
  - knowledge/execution.md         # if executor / slice-closing
  - knowledge/stack.md             # domain as needed
  - knowledge/api-conventions.md
rules:
  - .cursor/rules/skills-first.mdc
  - .cursor/rules/api-conventions.mdc   # when editing API
---

# Skill Name

## Knowledge & rules

Before executing: read every path in frontmatter `knowledge:` — especially
[workflow.md](../../../knowledge/workflow.md) / [execution.md](../../../knowledge/execution.md) when declared.
Obey frontmatter `rules:`.
If this skill changes musts/how-to → update-docs; lasting why → write-adr.

## Process

1. Follow shared playbooks (link only — do not copy).
2. [Skill-specific steps]

## Rules

| Rule | Detail |
|------|--------|
| Shared playbooks | Obey declared workflow/execution knowledge |
```

## knowledge: / rules: policy

| Field | Required | Notes |
|-------|----------|-------|
| `knowledge:` | Yes | Non-empty for almost all skills. Prefer shared playbooks over `[]`. |
| Workflow skills | Yes | Must include `knowledge/workflow.md` |
| Executor skills | Yes | Must include `knowledge/workflow.md` + `knowledge/execution.md` |
| `rules:` | Yes | At least `.cursor/rules/skills-first.mdc`. Add api/web when those layers are edited. |

**Do not** copy pipeline/mandate from `workflow.md` or conflict/review/verify from `execution.md` into the skill body — **link** them.

When a skill grows standards, update matching knowledge/rules via `update-docs` in the same change.

## Review gate

After drafting: run `review-skill`. Not ready to use until it reports **Ready to use: yes** (playbooks blocking).

## Description requirements

Max 1024 chars; third person; capability + `Use when …`.

## When to add scripts / split files

Deterministic helpers → `scripts/`. Body > ~100 lines → `REFERENCE.md`.
