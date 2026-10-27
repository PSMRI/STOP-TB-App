# Write a Skill — reference

## Template

```md
---
name: kebab-name            # must equal the folder name
description: >-
  What it does, in third person. Use when <concrete triggers the user would say>.
knowledge:
  - knowledge/workflow.md   # workflow/orchestration and executors
  - knowledge/execution.md  # executors / slice-closing skills
  - knowledge/stack.md      # domain as needed
  - knowledge/android-conventions.md
---

# Title

## Knowledge
Read the `knowledge:` files (link them). Do not copy pipeline or execute contract text.

## Process
1. Steps grounded in real files of this repo.

## Rules
| Rule | Detail |
```

## Kinds

| Kind | Must declare | Body |
|------|--------------|------|
| Workflow / orchestration | `workflow.md` | Points to the pipeline |
| Executor (closes a slice) | `workflow.md` + `execution.md` | Points to the contract |
| Craft (one technical area) | domain knowledge (`stack.md`, `android-conventions.md`, `testing.md`) | Specific steps, examples, pitfalls |

## Description

≤ 1024 chars, third person, capability + "Use when …" with words users actually type
(e.g. "slow list", "add column", "new form", "worker").

## Size

Body ≤ ~100 lines; long examples → `REFERENCE.md` next to `SKILL.md`. Deterministic helpers → `scripts/`.
