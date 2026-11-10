# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

StopTB is an offline-first Android app (Kotlin, XML views, MVVM, Hilt, Room, Retrofit, WorkManager). It is used for TB camp workflows: beneficiary registration, general examination, TB screening, general OPD, diagnostics, suspected/confirmed TB tracking, referrals, counselling and ABHA. It is part of the PSMRI **AMRIT** platform and was forked from `FLW-Mobile-App`. That is why the Gradle root project is named `Sakhi`, the DB file is `Sakhi-2.0-In-app-database`, and there are leftover Malaria/Leprosy/Filaria/Kala-azar/AES/CBAC DAOs, repos and workers that StopTB does not actively use.

Upstream is `PSMRI/STOP-TB-App`. PRs are reviewed by CodeRabbit. Branch names follow `bugFix/STOP-<id>_...` or `feat/...`, and commit messages reference Jira `STOP-<id>`.

## Build & test

Flavors: `stoptb` (prod, `org.piramalswasthya.stoptb`) and `stoptbUat` (`org.piramalswasthya.stoptb.uat`). Combined with debug/release, the variants are `stoptbDebug`, `stoptbUatDebug`, `stoptbRelease` and `stoptbUatRelease`.

```bash
./gradlew :app:compileStoptbUatDebugKotlin          # fastest compile check
./gradlew assembleStoptbUatDebug                    # build APK
./gradlew :app:testStoptbUatDebugUnitTest           # JVM unit tests
./gradlew :app:testStoptbUatDebugUnitTest --tests "org.piramalswasthya.stoptb.configuration.RoleModuleConfigTest"
./gradlew :app:connectedStoptbUatDebugAndroidTest   # instrumented tests (Room/DAO/migration tests live here)
./gradlew :app:lintStoptbUatDebug
```

Prerequisites for a build to succeed:
- **NDK `29.0.14033849`**. Server URLs and ABHA credentials are compiled into native code (`app/src/main/cpp/native-lib.cpp`, created from `native-lib.cpp.example`) and read through `KeyUtils` external functions (`baseTMCUrl()`, `baseAbhaUrl()` etc.). `.env.example` lists the keys. Never commit real values in `native-lib.cpp`.
- `google-services.json` for the flavor, and `keystore.properties` for release signing (see `keystore.properties.example`).
- JDK 17 for Gradle. App bytecode targets Java 1.8. Annotation processing uses **kapt** (Room, Hilt, Moshi, Glide), not KSP.
- The version comes from `version/version.properties` through `versioning.gradle`.

CI: `.github/workflows/distribute.yml` is a manual workflow that builds uat/prod, debug/signed and distributes to Firebase App Distribution, Intune and Play Internal. `promote-intune.yml` pushes an existing Firebase APK to Intune. The README also mentions an `android-ci.yml`, but that file is not in the repo. See `docs/CICDSetup.md`.

## Architecture

Package root: `app/src/main/java/org/piramalswasthya/stoptb/`.

**Layers.** `ui/` (Activities/Fragments + ViewModels, Navigation Component graphs in `res/navigation/`) → `repositories/` → `database/room` DAOs and `network/` Retrofit services. All DI is in a single `di/AppModule.kt`. `LoginActivity` is the launcher. After login and service-location selection, the active home is `ui/volunteer` (`VolunteerActivity`, `nav_volunteer.xml`).

**Roles drive the UI.** `configuration/RoleModuleConfig.kt` is the single source of truth for which home modules each `AppRole` sees: REGISTRAR, NURSE, COUNSELING, LAB_TECHNICIAN, and VOLUNTEER as a placeholder. It also handles multi-role accounts. `helpers/RoleManager.kt` applies it. To add a role, add an `AppRole` entry and a map entry. Both have unit tests.

**Forms.**
- Static forms are `Dataset` subclasses in `configuration/` (e.g. `TBScreeningDataset`, `SuspectedTBDataset`, `GeneralOpdDataset`, `BenRegFormDataset`) that build `FormElement` lists rendered by a generic form adapter.
- Server-driven dynamic forms are downloaded from `flw-api/dynamicForm/*`, stored in `FormSchemaDao`/`FormResponseDao`, and evaluated by `helpers/dynamicEngine` (`ConditionalLogicEngine`, `ValidationEngine`). `helpers/dynamicMapper` builds the submit payloads.

**Room DB.** `database/room/InAppDb.kt` is a single large file with ~60 hand-written migrations (`version = 61`, `exportSchema = false`).
- Any entity change needs a version bump, a new `MIGRATION_N_N+1`, and an entry in the `addMigrations(...)` chain near the bottom of the file.
- Use `tableExists`/`columnExists` guards. SQLite forbids column defs after table constraints; see the comment near those helpers.
- **Release builds encrypt the DB with SQLCipher** (`DatabaseKeyManager` + `RoomDbEncryptionHelper`). Debug builds are plain SQLite, so debug DB files can be read with `sqlite3`.
- Migration tests are in `androidTest/.../database/room/`.

**Network.**
- All AMRIT calls go through `network/AmritApiService.kt`. Paths are prefixed with the backend microservice: mostly `flw-api/` (`stoptb/*`, `tb/*`, `dynamicForm/*`, `follow-up/*`), plus `common-api/`, `fhir-api/`, `identity-api/`, `tm-api/`, `mmu-api/`, `hwc-api/`.
- ABHA uses a separate `AbhaApiService` and client.
- Interceptors (`network/interceptors/`) add the JWT/token (`TokenInsertTmcInterceptor`), handle account deactivation, and log/report analytics.

**Camp mode is how the app syncs.**
- `CampModeUrlInterceptor` is attached to every AMRIT OkHttp client. It rewrites each request's scheme/host/port to the stored **camp hub URL** and throws if none is set.
- When a request fails, it pings `<hub>/common-api/health` and marks the hub disconnected.
- `work/WorkerUtils.triggerAmritPushWorker` does nothing unless camp mode is enabled **and** the hub is connected. Gate on `PreferenceDao.isCampModeEnabled()` / `isCampHubConnected()` for any new network or sync code.

**Sync (`work/`).** `WorkerUtils` builds chained unique WorkManager jobs.
- Push: `PushToAmritWorker` (registration) → in parallel `PushTBToAmritWorker` and `PushMapAbhatoBenficiaryWorker`.
- Pull: `PullFromAmritWorker` → TB pull → `UpdatePrefForPullCompleteWorker`.
- Diagnostic order push / result-poll workers handle TrueNat/RIF lab results.
- Entities track sync state with `SyncState` (via converter). The sync dashboards in `ui/home_activity/sync_dashboard/` read `SyncDao`.

**Backend counterparts.** The server code for these endpoints is in PSMRI `FLW-API` (StopTB controllers/services), `Common-API`, `Identity-API` etc. Server DB schema migrations are in `AMRIT-DB`. When debugging payload or field mismatches, trace the Retrofit path to the matching controller there.

## Repo-local Claude skills

`.claude/skills/` contains project skills to use for matching tasks: `room-migration`, `offline-sync-debug`, `crash-anr-triage`, `localization-check`, `release-readiness`, `health-data-security-review`, `adb-device-debug`, `gradle-build-doctor` and others.

## CFU and TPT work: required reading

Before analysing or changing anything related to CFU (Contact Follow-up, `FormType.CONTACT_FOLLOW_UP`) or TPT (TPT Registration / Start TPT, TPT Follow-up, TPT History, `FormType.TPT_FOLLOW_UP`), invoke the `cfu-tpt-module` skill and read it in full. It holds the finalized STOP-602/STOP-620 backward-compatibility rules (Confluence page 198574096) and the code map. This applies to tickets, reviews, debugging and questions about these modules. If a request conflicts with those rules, raise it before changing code.

## Persistent knowledge

Changes to `CLAUDE.md`, `.claude/skills/` and Claude's memory are approval-first: show the exact wording and target file, and write only after the user approves. The procedure is in the `capture-learnings` skill.

## Conventions worth knowing

- Supported languages are English, Hindi and Assamese (`resourceConfigurations = ['en','hi','as']`). New user-facing strings need `values-hi` and `values-as` entries.
- Beneficiary list buttons reflect workflow state. For example, General OPD appears only after TB Screening is complete: red while pending, green once filled. Diagnostics data is saved into the suspected-TB records that feed the Suspected/Confirmed TB modules.
- Use `navController.navigateSafe(...)` from `utils/NavExtensions.kt` (added for STOP-780) to avoid double-tap navigation crashes.
