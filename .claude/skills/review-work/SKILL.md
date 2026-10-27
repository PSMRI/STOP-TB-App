---
name: review-work
description: >-
  Reviews a finished change against the knowledge and musts of the skills that ran (Android
  conventions, DB safety, tests) and publishes a pass/fail table before verification. Use when
  implement-feature, fix-feature, refactor-code or any executor has finished, before verify-feature or
  declaring done.
knowledge:
  - knowledge/workflow.md
  - knowledge/execution.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Review Work

## Knowledge

Read the union of `knowledge:` of every matrix skill that ran, plus
[android-conventions.md](../../../knowledge/android-conventions.md) and
[execution.md](../../../knowledge/execution.md).

## Process

1. Changed files: `git diff --stat` + `git status` (only this task's changes).
2. For each applicable must, mark pass / fail / n/a. Minimum checks:

| Area | Check |
|------|-------|
| Fragments | Collectors via `launchOnView` / view scope; cleanup in `onDestroyView` |
| Lists | Set/Map lookups; no `notifyDataSetChanged`; no per-bind allocations |
| DAO | Village-scoped; batched in transaction; no needless REPLACE; indexes for joins |
| Schema | Version bumped, migration added to builder, migration test exists |
| Workers | KEEP vs REPLACE correct; no duplicate refresh triggers; IO dispatcher |
| Tests | Required tests from testing.md exist and were RED before GREEN |
| Logging | No large lists/bodies logged |
| Scope | Only matrix skills used; no unrelated edits |

3. Publish:

```
Review-work:
| Check | Source | Result | Notes |
```

Any fail → fix via the owning skill, re-run this review. All pass/n/a →
[verify-feature](../verify-feature/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| Table required | No silent pass |
| Diff-scoped | Review the change, not the whole repo |
