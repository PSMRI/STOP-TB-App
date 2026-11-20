---
name: implement-feature
description: Implement a planned or clearly specified STOP-TB feature or refactor end-to-end using the Android stack skills, test-first. Use when the user says implement, build, ship the plan, or after plan-feature/grill-me.
---

# Implement Feature

## Process

1. **Inputs.** Use the plan in `docs/plans/` if one exists; otherwise restate the goal, target branch and assumptions in 3–5 lines. Unresolved product decisions → `grill-me`.
2. **Show the touch list and wait for OK** before editing (files in order, one line each, DB version bump yes/no).
3. **Order of work** (skip what is out of scope):
   1. `add-unit-test` — write the failing test(s) for the new logic first (always for a new feature).
   2. `room-migration` — entity/column/view changes + migration + version bump (detect the branch's style first).
   3. `add-sync-flow` — API model, repository push/pull, worker wiring (detect the branch's sync style first).
   4. `add-form-screen` — Dataset, ViewModel, Fragment, nav graph, strings (en/hi/as).
   5. Make the tests pass; refactor while green.
4. **Refactors** use the same flow with "no behaviour change" as the goal: existing tests must stay green. Characterization tests only if the user asks (a refactor isn't a new feature).
5. **Finish** with `review-work` then `verify-feature`. Do not report done before both pass.

## Rules (team musts)

- No destructive migrations; never edit a shipped migration.
- Every new string in `values`, `values-hi`, `values-as`; don't edit or reword existing strings or shared constants.
- New/edited records set `syncState = UNSYNCED`; failed pushes stay `UNSYNCED`.
- No DB/network on the main thread.
- No secrets or PII in code or logs.
- CFU shares code with Community/Occupation contact tracing: CFU-only behaviour goes in guarded CFU-only functions.
- Reuse existing fragments/adapters/cards; clone the nearest existing feature's structure; no new architecture without agreement.
- Edit with the Edit tool, one file at a time.
- New concern discovered mid-way (e.g. needs a new permission or API) → stop and confirm with the user.
- Do not commit unless asked; branches are `feat/<task-wise-work>`.
