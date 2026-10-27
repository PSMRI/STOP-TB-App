# Execution contract (shared by all executor skills)

Executor skills (`implement-feature`, `fix-feature`, `refactor-code` and craft skills that close a
slice) follow this file. Do not restate it inside skills — link here.

## 1. Before editing

1. Have the skill matrix from `resolve-skills` (see [workflow.md](workflow.md) §2).
2. Read every `knowledge:` path of the matrix skills + [android-conventions.md](android-conventions.md).
3. `git status` — note pre-existing local changes; never overwrite or revert work you did not make.

## 2. Contract conflict check

If two knowledge files / skills disagree, or the request contradicts a must in
`android-conventions.md`: **stop and raise it** to the user (one question, with a recommendation).
Resolve via `update-docs` (how) or `write-adr` (why) before continuing.

## 3. Test first where it is testable

Follow [testing.md](testing.md): write or update the failing test (RED) for DAO queries,
migrations, repository/business logic and bugs that are reproducible in a test; then the minimal
change (GREEN). UI-only changes use the manual smoke checklist in testing.md instead.

## 4. Make the change

- Minimal diff; no drive-by refactors or formatting churn.
- Only the HAVE skills from the matrix decide structure. New concern mid-way → re-run `resolve-skills`.
- Keep schema, DAO, repository, ViewModel, UI changes in that order.

## 5. Close the slice

`review-work` (compliance) → `verify-feature` (evidence) → report. **Never commit or push unless
the user asks.**

## Commands

Run from the repo root (`./gradlew` needs JDK 17 + Android SDK; `local.properties` points to the SDK).

| Purpose | Command |
|---------|---------|
| Compile (UAT debug) | `./gradlew :app:assembleStoptbUatDebug` |
| Kotlin compile only (fast) | `./gradlew :app:compileStoptbUatDebugKotlin` |
| JVM unit tests | `./gradlew :app:testStoptbUatDebugUnitTest` |
| Instrumented tests (device/emulator) | `./gradlew :app:connectedStoptbUatDebugAndroidTest` |
| One instrumented class | `./gradlew :app:connectedStoptbUatDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.piramalswasthya.stoptb.database.room.dao.TBDaoTest` |
| Lint | `./gradlew :app:lintStoptbUatDebug` |
| Validate skills/knowledge wiring | `python3 scripts/validate_skills.py` |

If the environment cannot run Gradle (no SDK), say so explicitly in the verify report and list the
commands the developer must run — do not claim the build passed.
