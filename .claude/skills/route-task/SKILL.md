---
name: route-task
description: >-
  Entry router for any request that will change the STOP-TB repo: classifies it (feature,
  bug/perf, refactor, docs, skills) and hands off to resolve-skills before any plan or code.
  Use when the user asks to build, add, change, fix, speed up, refactor, migrate or ship
  anything in this Android app — load this before touching code.
knowledge:
  - knowledge/workflow.md
  - knowledge/stack.md
---

# Route Task

## Knowledge

Read [workflow.md](../../../knowledge/workflow.md) (§1 classify table) and
[stack.md](../../../knowledge/stack.md). The pipeline lives in workflow.md — do not restate it.

## Process

1. Restate the goal in one line and **classify** it with the workflow.md §1 table. Announce:
   `Kind: <kind> — <one-line goal>`.
2. Read-only question → answer directly, stop here.
3. Mutating → hand off to [resolve-skills](../resolve-skills/SKILL.md). Do not plan or write
   app code until the matrix exists.
4. After the matrix: plan via [plan-feature](../plan-feature/SKILL.md) when workflow.md says so,
   otherwise the executor named in the classify table.

## Rules

| Rule | Detail |
|------|--------|
| Classify only | No code, no skill matching here |
| Always hand off | Every mutating kind goes through resolve-skills |
| Re-enter | A new concern mid-work → resolve-skills again |
