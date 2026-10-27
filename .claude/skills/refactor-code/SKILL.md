---
name: refactor-code
description: >-
  Restructures STOP-TB code without changing behaviour — extracting helpers, moving logic out of
  fragments into ViewModels/repositories, renaming, splitting large files — in small verified
  steps. Use when the user asks to refactor, clean up, restructure or rename without a new feature.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
  - knowledge/android-conventions.md
---

# Refactor Code

## Knowledge

Follow [execution.md](../../../knowledge/execution.md); target shape per
[android-conventions.md](../../../knowledge/android-conventions.md).

## Process

1. Confirm scope (paths) and "no behaviour change". If behaviour must change, it is a feature/fix.
2. Structural concerns (DAO, lists, lifecycle, workers) → use their matrix skills.
3. Ensure tests cover the code being moved (add characterisation tests via
   [android-test](../android-test/SKILL.md) for repository/DAO logic).
4. Small steps; compile between steps; no drive-by features or formatting churn.
5. Room entity/column renames are **schema changes** → [room-migration](../room-migration/SKILL.md).
6. [review-work](../review-work/SKILL.md) → [verify-feature](../verify-feature/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| Same behaviour | Same outputs, same persisted data |
| Minimal diff | No unrelated cleanup |
