# Testing — how this repo tests

## Where tests live

| Kind | Path | Runs with | Use for |
|------|------|-----------|---------|
| JVM unit | `app/src/test/java/org/piramalswasthya/stoptb/...` | `testStoptbUatDebugUnitTest` | Pure Kotlin logic: datasets' value mapping, role/config rules, DTO parsing, repository helpers with mocks (JUnit4 + Truth + Mockito-Kotlin) |
| Instrumented | `app/src/androidTest/java/org/piramalswasthya/stoptb/...` | `connectedStoptbUatDebugAndroidTest` | Room: DAO queries, transactions, migrations (real SQLite) |

Reference tests to copy patterns from:
- DAO test with in-memory DB: `androidTest/.../database/room/dao/TBDaoTest.kt`
- Migration test (hand-built old schema, `PRAGMA user_version`, `addMigrations`):
  `androidTest/.../database/room/PrescriptionMigrationTest.kt` + `GeneralOpdLegacySchema.kt`

## What must have a test (RED first)

| Change | Required test |
|--------|---------------|
| New/changed migration | Migration test: old version → new version, data preserved, new columns/indexes present |
| New/changed DAO query | DAO test: village scoping, filter/sort result, empty case |
| Batched / transactional writes | DAO/repo test that the end state is correct and unchanged rows are not rewritten (where observable) |
| Bug in repository/business logic | Failing unit test reproducing it |
| Pure UI / layout / lifecycle | No automated test required → manual smoke below |

## Manual smoke checklist (UI and performance changes)

Use a device/emulator with a **large dataset (≥ 5,000 beneficiaries in one village)**:

1. Open All Beneficiaries and the TrueNat / X-ray referral lists — first page visible, scrolling smooth.
2. Search by name, mobile, ben ID — results update without freezing the keyboard.
3. Go into a form (Examine → TB Screening) and back **10×** — list stays as fast as the first open.
4. Leave the list open ≥ 3 minutes with camp-hub polling active — no periodic freezes.
5. Rotate / background-foreground the app once — no crash, list position kept.
6. Kill and reopen the app — unsynced records still present (no destructive migration).

Report which steps were run and on what device; if not run, say so.

## Profiling evidence (performance work)

Android Studio Profiler (CPU, main thread) + App Inspection → Database Inspector (query count and
duration while scrolling/searching). Capture before/after numbers in the plan or PR description.
