---
name: android-test
description: >-
  Writes and runs STOP-TB tests: JVM unit tests (JUnit4, Truth, Mockito-Kotlin) for datasets,
  repositories and helpers, and instrumented Room tests for DAO queries, transactions and
  migrations, following the repo's existing test patterns. Use when a change needs a failing test
  first, when adding a migration or DAO query, when fixing a reproducible bug, or when the user
  says add/run tests.
knowledge:
  - knowledge/testing.md
  - knowledge/execution.md
---

# Android Test

## Knowledge

[testing.md](../../../knowledge/testing.md) (what needs a test, smoke list),
[execution.md](../../../knowledge/execution.md#commands) (commands).

## Process

1. Decide the level with the testing.md table (JVM unit vs instrumented Room vs manual smoke).
2. **JVM unit** → `app/src/test/java/org/piramalswasthya/stoptb/<package>/<Subject>Test.kt`.
   Pattern: `helpers/RoleManagerTest.kt`, `repositories/GeneralOpdPullBatchTest.kt`.
   Mock DAOs/services with Mockito-Kotlin; assert with Truth.
3. **DAO** → `app/src/androidTest/.../database/room/dao/`. Pattern: `TBDaoTest.kt` —
   `Room.inMemoryDatabaseBuilder(context, InAppDb::class.java).allowMainThreadQueries().build()`
   in `@Before`, `db.close()` in `@After`, insert minimal `BenRegCache` rows with a `LocationRecord`
   for the village, `runBlocking` + `flow.first()` for Flows. Always include a row from **another
   village** to prove scoping.
4. **Migration** → pattern `PrescriptionMigrationTest.kt`: create DB, rewrite to the old schema
   (helper like `GeneralOpdLegacySchema.kt`), `PRAGMA user_version = <old>`, reopen with
   `.addMigrations(...)`, assert data kept and new columns/indexes exist
   (`PRAGMA table_info`, `PRAGMA index_list`).
5. **RED → GREEN**: run the test and see it fail for the right reason before the fix; then pass.
6. Run (see execution.md): unit `testStoptbUatDebugUnitTest`; instrumented
   `connectedStoptbUatDebugAndroidTest` (single class with `-Pandroid.testInstrumentationRunnerArguments.class=...`).
   No device/SDK available → report as not run with the command.

## Rules

| Rule | Detail |
|------|--------|
| Real SQLite for DB | No mocking Room for query/migration tests |
| Scoping proven | DAO tests include another village |
| Honest results | Never report a test as passed without running it |
