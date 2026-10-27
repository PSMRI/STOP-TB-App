# STOP-TB — instructions for Claude

Offline-first Android app (Kotlin, Room + SQLCipher, Paging 3, WorkManager, Hilt) for TB
screening, diagnostics referral and follow-up. Details: [knowledge/stack.md](knowledge/stack.md).

## How to work in this repo

- **Every request that changes the repo starts with the `route-task` skill**, which hands off to
  `resolve-skills` (skill matrix) before any plan or code. Pipeline: [knowledge/workflow.md](knowledge/workflow.md).
- Execution contract (test first, minimal diff, review → verify, no commits unless asked):
  [knowledge/execution.md](knowledge/execution.md).
- Skills catalog: [.claude/skills/README.md](.claude/skills/README.md). Decisions: [docs/adr/](docs/adr/README.md).
  Plans: [plans/](plans/README.md).
- Read-only questions can be answered directly.

## Top musts (full list: [knowledge/android-conventions.md](knowledge/android-conventions.md))

1. Schema change ⇒ version bump + Migration + migration test. The DB builder has
   `fallbackToDestructiveMigration()` — a missing migration wipes unsynced field data.
2. Fragments collect flows with `launchOnView { }`; never `lifecycleScope.launch { collect }`.
3. UI queries are village-scoped; multi-row writes use `db.withTransaction`; don't REPLACE unchanged rows.
4. Large lists use Paging; adapters use Set/Map lookups and targeted `notifyItemChanged`.
5. UI-triggered unique work uses `ExistingWorkPolicy.KEEP`; one throttled refresh per screen.
6. Lists must stay smooth with 5,000+ beneficiaries — check against that, not a demo dataset.

## Commands

`./gradlew :app:compileStoptbUatDebugKotlin` · `./gradlew :app:testStoptbUatDebugUnitTest` ·
`./gradlew :app:connectedStoptbUatDebugAndroidTest` · `python3 scripts/validate_skills.py`
