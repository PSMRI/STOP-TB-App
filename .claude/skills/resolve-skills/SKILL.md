---
name: resolve-skills
description: >-
  Builds the needed-vs-have skill matrix for a mutating STOP-TB request: lists the concerns
  in scope (Room schema, DAO queries, paged lists, fragments, forms, workers, tests, docs),
  matches them to local skills, and closes any GAP before execution. Use when route-task has
  classified the work, or mid-implementation when a new concern appears.
knowledge:
  - knowledge/workflow.md
  - knowledge/stack.md
  - knowledge/testing.md
---

# Resolve Skills

## Knowledge

Read [workflow.md](../../../knowledge/workflow.md) §2 (matrix format, hard stop),
[stack.md](../../../knowledge/stack.md), [testing.md](../../../knowledge/testing.md).
**No app code in this skill.**

## Process

### 1. Needed — mark each concern in / n/a

| Concern | Skill |
|---------|-------|
| Room schema: new table/column/index/view, version bump | `room-migration` |
| DAO query or write path (incl. batching, transactions, village scope) | `room-dao-query` |
| Large/paged list screen, `BenPagingAdapter`/`BenListAdapter`, list performance | `paged-ben-list` |
| Fragment lifecycle, flow collection, binding/dialog cleanup | `fragment-lifecycle` |
| Data-entry form (`*Dataset`, `FormInputAdapter`, save) | `add-form-screen` |
| Camp-hub diagnostics, WorkManager workers, refresh/poll triggers | `diagnostic-worker` |
| Tests (unit, DAO, migration) | `android-test` |
| Refactor without behavior change | `refactor-code` |
| knowledge / CLAUDE.md / README | `update-docs` |
| Lasting trade-off | `write-adr` |
| New / changed skill | `write-a-skill` / `update-skill` → `review-skill` |
| Orchestration | `plan-feature`, `grill-me`, `implement-feature`, `fix-feature`, `review-work`, `verify-feature` |

Anything else (network/API contract, ABHA, auth, navigation graph, CI/fastlane, localisation…)
→ **GAP** unless a skill now exists in [the catalog](../README.md).

### 2. Have — check `.claude/skills/README.md`; each in-scope concern is HAVE or GAP.

### 3. Publish the matrix + execution order (format in workflow.md §2). Tests (`android-test`)
come first whenever testing.md requires a test.

### 4. Close GAPs: draft with [write-a-skill](../write-a-skill/SKILL.md) → [review-skill](../review-skill/SKILL.md),
or record the user's explicit "proceed without a skill" as an assumption. Hard stop otherwise.

### 5. Hand off to the planner/executor from the classify table.

## Rules

| Rule | Detail |
|------|--------|
| Both columns | Needed and Have are always shown |
| Matrix skills only | Executors use only HAVE skills listed here |
| No code | Matrix only |
