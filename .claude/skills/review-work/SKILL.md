---
name: review-work
description: Review the current diff against STOP-TB's musts and conventions before verification — data safety, migrations, sync state, strings, threading, secrets, tests, shared CFU code. Use after implement-feature or fix-feature, or when the user asks to review changes.
---

# Review Work

## Process

1. Collect the change set: `git status` + `git diff` (and the plan's touch list if any). Note whether it's a **new feature** or a **bug fix / other change**, and the branch's migration style (`room-migration` Step 0).
2. Check each item that applies; publish the table — no silent pass.

| Check | Pass when |
|---|---|
| Migration | Any `@Entity`/`@DatabaseView`/index change has a new `MIGRATION_N_N+1`, registered the branch's way (Style A: appended to `MIGRATIONS` + `DB_VERSION` bumped; Style B: `.addMigrations(...)` + `version = N+1`); number not used on another release branch; no new destructive fallback; shipped migrations untouched |
| Sync | New/edited records set `syncState = UNSYNCED`; failures leave rows `UNSYNCED`; worker added to the right branch of the push chain |
| Strings | Every new `<string>` exists in `values`, `values-hi`, `values-as`; no hard-coded UI text; existing strings not edited |
| Threading | No DB/network on main thread; coroutines scoped to `viewModelScope`/`lifecycleScope` |
| IDs | Saves re-resolve temp `benId` where the pattern requires it |
| Nav | New fragments registered in the nav graph with typed args |
| DI | New repos/DAOs provided through Hilt; workers use `@HiltWorker` + `@AssistedInject` |
| Privacy | No secrets committed; no PII in logs |
| Tests | **New feature:** tests cover the new logic (FAIL if missing). **Bug fix / other:** n/a unless the user asked for tests |
| Shared CFU code | No change to code shared by CFU and Community/Occupation contact tracing unless intended; CFU-only logic in guarded CFU-only functions |
| Scope | Minimal: no unrelated refactors, renames, flow changes, shared-constant edits or dependency bumps |

```text
Review:
| Check | Result | Notes |
|-------|--------|-------|
```

3. Any **fail** → propose the fix (owning skill), get OK, apply it and re-review. All pass / n/a → `verify-feature`.
