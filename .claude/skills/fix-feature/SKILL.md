---
name: fix-feature
description: >-
  Diagnoses and fixes a STOP-TB bug or performance problem with the smallest safe change:
  reproduce, find the root cause (DB, query, worker, lifecycle, adapter), write a failing test
  where possible, patch, then review and verify. Use when the user reports a crash, wrong data,
  sync failure, slow or freezing screen, lag with many records, or asks to fix something.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/stack.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Fix Feature

## Knowledge

Follow [execution.md](../../../knowledge/execution.md) and [testing.md](../../../knowledge/testing.md);
check fixes against [android-conventions.md](../../../knowledge/android-conventions.md).

## Process

1. Require the matrix from [resolve-skills](../resolve-skills/SKILL.md).
2. Reproduce: exact screen, steps, data size, flavor, device. Unclear → [grill-me](../grill-me/SKILL.md).
3. Root cause before patching. For slowness/lag check, in this order:
   - what triggers work: duplicate refreshes, workers re-enqueued, collectors piling up
     ([fragment-lifecycle](../fragment-lifecycle/SKILL.md), [diagnostic-worker](../diagnostic-worker/SKILL.md));
   - write pattern: row-by-row writes, REPLACE of unchanged rows ([room-dao-query](../room-dao-query/SKILL.md));
   - query cost: view/joins/indexes/subqueries ([room-dao-query](../room-dao-query/SKILL.md), [room-migration](../room-migration/SKILL.md));
   - main-thread work in adapters/ViewModels ([paged-ben-list](../paged-ben-list/SKILL.md)).
   State the cause with file:line evidence before editing.
4. RED: failing test via [android-test](../android-test/SKILL.md) when testable; otherwise a written
   manual repro from testing.md.
5. GREEN: minimal patch through the owning matrix skill. Schema → room-migration.
6. [review-work](../review-work/SKILL.md) → [verify-feature](../verify-feature/SKILL.md).
   Lasting lesson → [write-adr](../write-adr/SKILL.md) and/or a must in android-conventions via
   [update-docs](../update-docs/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| Cause first | No speculative patches |
| Minimal | Fix the bug, not the neighbourhood |
| Evidence | Before/after numbers for perf fixes (testing.md profiling) |
