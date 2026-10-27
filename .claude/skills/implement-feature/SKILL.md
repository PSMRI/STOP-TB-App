---
name: implement-feature
description: >-
  Implements a planned STOP-TB feature end to end by running the matrix skills in order
  (tests first, then schema, DAO, repository, workers, ViewModel, UI) under the shared
  execution contract. Use when the user says implement / build / ship the plan, or after
  plan-feature and grill-me have frozen the gates.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Implement Feature

## Knowledge

Follow [execution.md](../../../knowledge/execution.md) (conflict check → test first → change →
review → verify) and [workflow.md](../../../knowledge/workflow.md). Musts:
[android-conventions.md](../../../knowledge/android-conventions.md).

## Process

1. Require the matrix (from the plan or [resolve-skills](../resolve-skills/SKILL.md)) and frozen
   gates ([grill-me](../grill-me/SKILL.md) if not).
2. Execute HAVE skills in matrix order, typically:
   [android-test](../android-test/SKILL.md) (RED) →
   [room-migration](../room-migration/SKILL.md) →
   [room-dao-query](../room-dao-query/SKILL.md) →
   repository / [diagnostic-worker](../diagnostic-worker/SKILL.md) →
   [add-form-screen](../add-form-screen/SKILL.md) / [paged-ben-list](../paged-ben-list/SKILL.md) →
   [fragment-lifecycle](../fragment-lifecycle/SKILL.md).
3. Tick the plan's touch list as you go; new concern → resolve-skills before more code.
4. Close with [review-work](../review-work/SKILL.md) → [verify-feature](../verify-feature/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| Matrix skills only | No freestyle structure |
| Contract | execution.md is not restated here — follow it |
| No commit | Unless the user asks |
