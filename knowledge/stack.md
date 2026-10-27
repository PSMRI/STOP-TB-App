# Stack — STOP-TB Android app (as built)

Offline-first field app for beneficiary registration, TB screening, diagnostics referral
(camp hub X-ray / TrueNat / RIF), suspected/confirmed TB follow-up and general OPD.
Data is captured offline on the device and synced to the Amrit backend.

## Build

| Item | Value |
|------|-------|
| Module | single `:app`, package `org.piramalswasthya.stoptb` |
| Language | Kotlin, `kapt`, jvmTarget 1.8 |
| SDK | compileSdk 36, targetSdk 36, minSdk 25 |
| Flavors | `stoptb` (prod), `stoptbUat` (UAT) — dimension `default` |
| Build types | `debug`, `release` (minify + shrink) |
| UI | Fragments + Navigation/SafeArgs (`res/navigation/nav_*.xml`), DataBinding + ViewBinding |
| DI | Hilt 2.48 (`@HiltViewModel`, `@AndroidEntryPoint`, `@HiltWorker`) |
| DB | Room 2.6.1 + SQLCipher (`openHelperFactory`), `InAppDb` (`database/room/InAppDb.kt`) |
| Lists | Paging 3.2.1 + `room-paging` |
| Background | WorkManager 2.10.2 + hilt-work |
| Network | Retrofit 2.9 + Moshi/Gson, OkHttp 4.12 |
| Logging | Timber; `SyncLogTree` → `SyncLogManager` (Sync Dashboard + log file) |

## Package map (`app/src/main/java/org/piramalswasthya/stoptb/`)

| Package | Holds |
|---------|-------|
| `ui/` | Activities, Fragments, ViewModels (by feature under `ui/home_activity/...`) |
| `adapters/` | RecyclerView adapters (`BenListAdapter`, `BenPagingAdapter`, `FormInputAdapter`) |
| `configuration/` | Form definitions: `*Dataset` classes extending `Dataset` |
| `database/room/` | `InAppDb`, DAOs (`dao/`), converters; `database/shared_preferences/PreferenceDao` |
| `model/` | Room entities (`*Cache`), view `BenBasicCache`, domain models |
| `repositories/` | `BenRepo`, `TBRepo`, `RecordsRepo`, … — network + DB orchestration |
| `work/` | Push/Pull workers, `WorkerUtils` (enqueue helpers), diagnostic order/poll workers |
| `network/` | Retrofit services and DTOs |
| `helpers/` | `RoleManager`, sync logging, misc |
| `utils/` | Extensions (`launchOnView`, `callPhoneNumber`, …) |

## Data model essentials

- Core tables: `BENEFICIARY` (`BenRegCache`), `HOUSEHOLD`, `TB_SCREENING`, `TB_SUSPECTED`,
  `TB_DIAGNOSTICS`, `BEN_VITALS`, `GENERAL_OPD`, `CBAC`.
- `BEN_BASIC_CACHE` is a **view** (`@DatabaseView` in `model/Ben.kt`, and recreated in
  `InAppDb.recreateBenBasicCacheView`). It joins 5 tables with `GROUP BY` — expensive; every
  list query built on it scales with the whole table.
- Everything shown in the UI is scoped to the **selected village**:
  `preferenceDao.getLocationRecord()!!.village.id` (`RecordsRepo.selectedVillage`).
- `SyncState`: `UNSYNCED` → `SYNCING` → `SYNCED`. Server pulls must not overwrite local
  `UNSYNCED` rows (`shouldApplyServerRecord`).
- Diagnostic orders (`TB_DIAGNOSTICS.xrayOrderStatus / trueNatOrderStatus / rifOrderStatus`) are
  reconciled by `TBRepo.fetchBeneficiariesByStatus` and `DiagnosticResultPollWorker`; writes per
  beneficiary go through `withBenIdLock(benId)`.

## Sharp edges (read before touching DB)

- `InAppDb` uses `.fallbackToDestructiveMigration()`: a **missing or failing migration wipes the
  device database, including unsynced field data.** Every schema change needs a migration + test.
- `exportSchema = false` — there are no schema JSONs; migration tests build old schemas by hand
  (see `androidTest/.../PrescriptionMigrationTest.kt`).
- Target scale: lists must stay smooth with **5,000+ beneficiaries** on a low-end device.

## Commands

See [execution.md](execution.md#commands).
