---
name: plan-feature
description: >-
  Scopes a STOP-TB feature or large fix into a written plan: goal, in/out of scope, touch list
  across schema/DAO/repo/ViewModel/UI/workers, gates for grill-me, and definition of done. Use
  when resolve-skills produced a matrix for a feature, or the user says plan this, scope this,
  design this before coding.
knowledge:
  - knowledge/workflow.md
  - knowledge/stack.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Plan Feature

## Knowledge

Read [workflow.md](../../../knowledge/workflow.md) §3, [stack.md](../../../knowledge/stack.md),
[android-conventions.md](../../../knowledge/android-conventions.md), [testing.md](../../../knowledge/testing.md).

## Process

1. Require the matrix from [resolve-skills](../resolve-skills/SKILL.md) — never plan without it.
2. Explore the code that the feature touches (entities, DAO, repo, ViewModel, fragment, dataset,
   workers, nav graph). Prefer an existing similar screen as the template.
3. Draft (no code): goal, in/out, touch list in layer order
   (`model` → `InAppDb` migration → DAO → repo → worker → ViewModel → fragment/adapter/layout → nav),
   tests per testing.md, DoD.
4. List open decisions as a gates table → [grill-me](../grill-me/SKILL.md).
   Typical gates: sync direction & conflict rule, offline behaviour, which roles see it
   (`RoleManager`), village scoping, migration of existing rows, flavor differences.
5. Write `plans/YYYY-MM-DD-<slug>.md` using [plans/README.md](../../../plans/README.md).
6. Lasting trade-off → [write-adr](../write-adr/SKILL.md).

## Rules

| Rule | Detail |
|------|--------|
| No code | Planning only |
| Durable | Plan saved under `plans/` |
| Data safety | Any schema change lists its migration + migration test |
