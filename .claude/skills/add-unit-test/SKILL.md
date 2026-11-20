---
name: add-unit-test
description: Write STOP-TB tests test-first — JVM unit tests (JUnit4, Truth, Mockito-Kotlin) for logic and instrumented tests for Room DAOs and migrations. Use automatically whenever a NEW feature is implemented; for bug fixes and other changes only when the user asks for tests or TDD.
---

# Add Unit Test

## When this runs

| Change | Tests |
|---|---|
| **New feature** (new form, list, field, sync flow, logic) | **Always** — write them as part of the implementation, without waiting to be asked |
| Bug fix, refactor, config/UI tweak | Only when the user asks. Otherwise verify by compile + manual steps and offer a test in one line |

## Where tests go

| What | Type | Location | Run |
|---|---|---|---|
| Pure logic: Dataset rules, helpers, parsers, role config, request/response mapping, repository logic with mocked deps | JVM | `app/src/test/java/org/piramalswasthya/stoptb/<same package>/` | `./gradlew :app:testStoptbUatDebugUnitTest` |
| Room DAO queries, migrations, anything needing `Context`/SQLite | Instrumented | `app/src/androidTest/java/org/piramalswasthya/stoptb/<package>/` | `./gradlew :app:connectedStoptbUatDebugAndroidTest` |

Prefer JVM tests; move logic out of Fragments/Android classes so it can be tested on the JVM.

## Examples to copy (check they exist on the current branch first)

| Example | Kind | Exists on |
|---|---|---|
| `test/.../configuration/KeyPopulationRiskFactorAutoSelectTest.kt` | Logic | 2.3, 2.4, 2.5 |
| `test/.../utils/ServerDateParserTest.kt` | Logic | 2.3, 2.4 |
| `test/.../network/GeneralOpdSaveRequestTest.kt` | Network model | 2.3, 2.4, 2.5 |
| `test/.../repositories/GeneralOpdStaticMasterModeTest.kt` | Repo with mocks | 2.3, 2.4 |
| `test/.../utils/sync/BatchIsolationTest.kt` | Sync | 2.3, 2.4 |
| `test/.../configuration/RoleModuleConfigTest.kt` | Role config | 2.2 and later |
| `androidTest/.../database/room/dao/TBDaoTest.kt` | DAO | 2.1 and later |
| `androidTest/.../database/room/PrescriptionMigrationTest.kt` | Migration | 2.3, 2.4, 2.5 |

If none fits on this branch, copy the closest test in `app/src/test/` and follow its style.

## Process (red → green → refactor)

1. **RED** — write the test describing the expected behaviour (or reproducing the bug, when asked). Run it and confirm it fails for the right reason.
2. **GREEN** — minimal production change to pass.
3. **REFACTOR** — tidy while green.
4. Run the whole unit suite before handing to `review-work`.

## Conventions

- Name: `<ClassUnderTest>Test`, methods describe behaviour: `fun pushFailure_leavesRowsUnsynced()`.
- Assertions: Truth (`assertThat(x).isEqualTo(y)`) or JUnit `assertEquals` as in neighbouring tests.
- Mocks: `org.mockito.kotlin` (`mock()`, `whenever`, `verify`). `mockito-kotlin` is only in `app/build.gradle` from release-2.2 on — on older branches ask before adding it.
- Coroutines: `runBlocking` (no coroutines-test dependency yet — ask before adding one).
- No real network, no real dates without a fixed clock, no PII in fixtures.
