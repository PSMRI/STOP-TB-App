---
name: plan-feature
description: Scope a new or unclear STOP-TB feature into a written plan (screens, Room changes, sync, strings, tests) before any code. Use when the user says plan, scope or design a feature, or the request is too vague to implement directly.
---

# Plan Feature

Planning only — **no production code** in this skill.

## Process

1. **Understand the flow.** Read `README.md` (role flows: Registration Officer, Nurse, Camp Mode) and the closest existing feature. Note which release branch the work targets — 2.3/2.4 and the other branches differ in DB and sync code (see `room-migration` and `add-sync-flow` Step 0). Good references:
   - Form screen: `ui/home_activity/non_communicable_diseases/tb_screening/form/` + `configuration/TBScreeningDataset.kt`
   - List screen: `ui/home_activity/non_communicable_diseases/tb_screening/list/`
   - Sync: `repositories/TBRepo.kt` + `work/PushTBToAmritWorker.kt`
2. **List the concerns in scope** and the skill that owns each:

   | Concern | Skill |
   |---|---|
   | Form / list screen, nav, strings | `add-form-screen` |
   | New table, column, view or index | `room-migration` |
   | Push/pull with the AMRIT server | `add-sync-flow` |
   | Tests (always for a new feature) | `add-unit-test` |
   | Version / CI / distribution | `release-build` |

   Anything not covered (e.g. new permission, new third-party SDK, camp-hub API change) → call it out as an open question.
3. **Draft the plan**: goal; target branch; in/out of scope; which roles see it (`RoleModuleConfig`, release-2.2+); file touch list; DB version bump (yes/no, and the number); sync impact; strings to add (en/hi/as); tests to write; done criteria.
4. **Lock open decisions** with `grill-me` (one question at a time).
5. **Write** `docs/plans/YYYY-MM-DD-<slug>.md` with the frozen decisions and the touch list in execution order. `docs/plans/` doesn't exist yet on any branch — create it, and ask the user whether plans should be committed. Hand off to `implement-feature`.

## Rules

- Mirror existing patterns (Dataset + FormInputAdapter forms, `BasePushWorker` sync) — do not introduce a second style (e.g. Compose, a new DI framework) without the user agreeing.
- Reuse existing fragments/adapters/cards for new lists.
- Keep CFU-only behaviour out of code shared with Community/Occupation contact tracing.
- Offline-first: every new flow must work without network and sync later.
