# StopTB (Sakhi) — Application Performance, Architecture & Reliability Audit

**Audit date:** 2026-10-29 · **Branch:** `hotfix/lagissue` · **Build audited:** `stoptbUatDebug`
**Device:** Lenovo TB336ZU (2560×1600, Android 16, arm64) · **Backend:** camp hub `172.18.0.157:8080`
**Dataset at time of measurement:** ~20,000 beneficiaries, X-ray 19,110 / True NAT 6,296 / HWC 18,358

⚠️ **Debug build caveat.** Measurements are from `stoptbUatDebug`: plaintext SQLite (no SQLCipher),
non-minified dex, Timber active. Absolute numbers are pessimistic versus release. Ratios, call
counts and thread states are the load-bearing evidence. **No release-build measurement was taken**
(§D9).

---

# Deliverable 1 — Executive summary

Opening a referral list (X-ray, True NAT) takes **4–6.5 seconds** on a 20k-beneficiary village,
**with nothing syncing**. Frame rendering is not the problem: the GPU sits at p99 10 ms and jank
is 0.33%. The time is spent waiting on SQLite.

Three findings dominate, in this order:

1. **The diagnostic status sweep runs on every screen open and blocks the list behind it.** On
   `onResume`, `fetchBeneficiariesByStatus` resolves its entire 19,110-entry bucket with ~42 large
   cursor-filling reads before the list's own query can get a database connection. Measured: a
   no-op `SELECT 1` waited **2,872 ms**; Paging's refresh took **3,588 ms** of a 4,068 ms screen
   open. Its order-id backfill is capped at 50 per sweep against a 19,110 backlog, so it needs
   ~382 sweeps to converge and pays the full scan cost on every open, forever. *(Confirmed)*

2. **Whole-table `Flow` observation makes every bulk sync quadratic.** `allTbDiagnostics` emits
   once per 500 rows written and re-reads the entire table each time, so ingesting N rows costs
   ≈ N²/1000 row-reads — ~400k at 20k rows, ~2.5M at 50k. This is what saturates the SQLite read
   connection pool. *(Confirmed, captured live)*

3. **Three silent data-loss defects in the sync layer.** A TB screening form reports "saved" when
   nothing was written; three push paths mark records SYNCED that were never sent (the code logs
   its own bug); counselling records are marked SYNCED with no API call. On an offline camp device
   there is no second copy of that data. *(Confirmed by code inspection)*

Alongside these, two **Critical** security findings are unrelated to performance but block
release: a git-tracked file currently holding secret values that `.gitignore` cannot protect, and
an unvalidated QR-scanned camp-hub URL that redirects the JWT *and the refresh token* to an
arbitrary host over cleartext.

**Most important fixes, in order:** restore the TB screening save and stop marking unsent records
SYNCED; untrack the secrets file and validate the QR host; defer/bound the status sweep; replace
whole-table decoration flows with page-keyed reads.

**Biggest remaining risk:** there is effectively no test suite (65 tests, all trivial), so none of
the sync defects above have regression coverage, and none of the proposed fixes can be validated
by tests alone.

---

# Deliverable 2 — Application architecture map

## 2.1 Execution path, traced (not assumed)

```
User taps a referral card (IconGridAdapter.onClicked)
  └─ NavDirections → AllBenFragment (source=6 X-ray / 7 TrueNat / 0 All Ben)
       ├─ onCreateView/onViewCreated  ........................ 135 ms measured
       │    ├─ AllBenViewModel via Hilt (RecordsRepo, TBRepo, VitalRepo, ContactTracingRepo)
       │    ├─ Pager(PagingConfig(30, prefetch 10)) → RecordsRepo.searchBenPagedSource
       │    │     └─ source 0-4 → BenDao.searchBenPagedWithoutTbTables  (no TB tables observed)
       │    │        source 5-10 → BenDao.searchBenPaged                (observes TB tables)
       │    │           └─ both over @DatabaseView BEN_BASIC_CACHE
       │    │              = BENEFICIARY b LEFT JOIN HOUSEHOLD h WHERE b.isDraft = 0 (~100 cols)
       │    └─ 17 row-decoration Flow collectors — DEFERRED until first refresh settles
       └─ onResume
            └─ AllBenViewModel.fetchBeneficiaryStatuses()   ← 30 s debounce, unfiltered only
                 └─ TBRepo.fetchBeneficiariesByStatus(orderType)
                      ├─ GET diagnostic/order/getBeneficiariesByStatus     435 ms
                      ├─ step 0 backfill: bulk-resolve WHOLE bucket (~42 cursor fills)
                      │    └─ 50 × POST order/result → 50 × TB_DIAGNOSTICS write
                      └─ buckets 1-5: per-beneficiary read-modify-write under withBenIdLock
```

**Sync side, parallel to all of the above:**
```
HomeActivity / VolunteerActivity  ── while(isActive) { trigger…; delay(10 s) } on Dispatchers.Main
   └─ WorkerUtils.trigger{Push,Pull}Worker
        └─ getWorkInfosForUniqueWork(name).get()   ← BLOCKING ListenableFuture on main thread
             └─ unique work chain (no PeriodicWorkRequest anywhere in the codebase)
                  ├─ PullFromAmritWorker  → BenRepo: 20 pages × 50 rows per transaction ✅
                  ├─ PullTBFromAmritWorker → Semaphore(3) over 9 domains, 500-row chunks ⚠️ §D4.2
                  └─ DiagnosticResultPollWorker → self-reschedules every 60 s, Semaphore(4)
```

## 2.2 Synchronization boundaries
- **Per-beneficiary:** `withBenIdLock(benId)` — a non-reentrant `kotlinx.coroutines.Mutex` map.
  Guards read-modify-write of `TB_DIAGNOSTICS`. Does **not** span the network call.
- **Per-push-method:** `tbScreeningPushMutex`, `tbSuspectedPushMutex` — serialise a push against
  itself (the documented dual-path race: ViewModel-direct + WorkManager).
- **Room executors:** custom — 8 query threads, 4 transaction threads (`InAppDb.kt:170-182`).
  Android's WAL connection pool is ~4 (1 primary for writes), so only ~3 concurrent readers are
  ever possible; the extra threads queue at the pool, not the executor. **This is the central
  performance fact of the whole audit.**
- **Cross-device / cross-process:** none. A local mutex provides no guarantee against the backend
  receiving duplicate operations from a retry (§D4.4).

---

# Deliverable 3 — Audit coverage inventory

Honest status. "Measured" = runtime evidence on device. "Read" = source inspected, no runtime
measurement. "Not audited" = not inspected.

## 3.1 Screens
| Screen | Status | Evidence |
|---|---|---|
| All Beneficiaries (source 0) | **Measured** | cold + steady, 8+ opens |
| Digital Chest X-ray (source 6) | **Measured** | 4 steady opens, full timeline |
| True NAT (source 7) | **Measured** | 3 steady opens |
| Referrals grid | **Measured** | badge counts, navigation |
| Volunteer home / glance | **Read** | clean implementation, §D5.9 |
| Dashboard | **Read** | 77-flow finding is static analysis, **never opened on device** |
| NCD / CD icon screens | **Read** | collector-leak finding is static |
| TB Screening / Suspected / Confirmed forms | **Read** | not opened on device |
| Household members, Non-HH, Unscreened | **Read** | share the audited adapters |
| Counselling, Contact Tracing | **Read** | sync defect found; UI not measured |
| ABHA, login, camp-mode connect | **Read** | security review only |
| New beneficiary / household registration | **Read** | not exercised |

## 3.2 Subsystems
| Subsystem | Status |
|---|---|
| Room query plans (`EXPLAIN QUERY PLAN` + `.timer`) | **Measured** on synthetic 9,987-row DB |
| SQLite connection-pool contention | **Measured** — thread dumps at 6 stalls |
| Paging 3 behaviour | **Measured** — refresh durations, invalidation counts |
| API latency / status codes | **Measured** — 434 calls over 9 min, plus a steady-state window |
| WorkManager scheduling | **Read** + `dumpsys jobscheduler` |
| Workers (30 files) | **Read** — none executed in isolation |
| Offline-first sync lifecycle | **Read** — scenarios reasoned, **not executed** |
| Frame timing / jank | **Measured** — `dumpsys gfxinfo`, reset + window |
| Memory | **Partially** — `dumpsys meminfo` only |
| Security / PII | **Read** — full checklist |
| Build / release config | **Read** |

## 3.3 Explicitly NOT done
- **Perfetto system trace** — not captured.
- **Macrobenchmark / Baseline Profiles** — not run.
- **LeakCanary / StrictMode / Android Studio Profiler** — not run. No memory-leak claim is made.
- **Release (R8 + SQLCipher) build** — never built or measured.
- **50k / 100k beneficiary stress test** — not performed. The largest real dataset measured is
  ~20k; a prior synthetic harness covered 10k/25k.
- **Dashboard, forms, counselling, registration** — not opened on device; all findings there are
  static analysis and are marked *Strongly supported*, not *Confirmed*.
- **Migration testing** (`MigrationTestHelper`) — not run.
- **Multi-device / concurrent-session scenarios** — not testable with one device.

---

# Deliverable 4 — Root-cause analysis

Confidence per the requested scale: **Confirmed** / **Strongly supported** / **Potential risk** /
**Unverified**.

## D4.1 [CONFIRMED · S2] The status sweep blocks the list on every open

**Feature:** X-ray / True NAT referral lists.
**Code:** `AllBenFragment.kt:919` → `AllBenViewModel.kt:330-355` → `TBRepo.kt:3215-3277`.
**Repro:** open Digital Chest X-ray on a village with a large awaiting bucket. 4/4 reproductions.

Annotated timeline, steady state, **nothing syncing**:
```
+156ms   onResume fires the status sweep
+606ms   getBeneficiariesByStatus returns (435 ms network)
         ↓ sweep resolves the whole 19,110-entry bucket:
         ↓ ~21 chunks × 2 large IN-clause reads = ~42 cursor fills
+2872ms  no-op SELECT 1 waited 2,872 ms
         4 threads RUNNABLE in SQLiteCursor.getCount -> nativeExecuteForCursorWindow
         rest parked in SQLiteConnectionPool.waitForConnection
+3391ms  backfill begins: 16 concurrent POST order/result
+3732ms  [paging] load REFRESH(count+page0) took 3588ms rows=90
+4068ms  FIRST CONTENT DRAWN  (flow emissions=1 — decoration deferral working)
+9726ms  childCounts items=19110 — decorations land 5.7 s after content
```

**Root cause:** the sweep is triggered from `onResume`, unbounded in read volume, and shares a
~3-connection read pool with the Paging refresh the user is waiting on.

**Why safeguards didn't catch it:** the backfill *was* bounded earlier in this work, but only on
**writes** (`orderIdBackfillPerSweep = 50`, `TBRepo.kt:1741`). The bucket resolution still scans
all 19,110 entries every sweep to find the next 50. Measured **110 `order/result` POSTs across 7
opens — ~16 every time**. At 50/sweep it needs ~382 sweeps to drain, so the cost never amortises.

**Correction to an earlier conclusion:** I previously tested deferring this sweep past first
paint, measured no median gain, and reverted it. **That test ran at ~960 X-ray members.** At
19,110 the sweep dominates. The earlier negative result does not hold at production scale.

**Proposed fix:** (a) make the bucket resolution incremental — persist a cursor so each sweep
resolves only the next slice rather than the whole bucket; (b) defer the sweep until after first
content; (c) skip it entirely when the previous sweep found nothing to backfill.
**Risk:** order ids appear later on cards. Mitigated by the Retry Referral fix already shipped,
which no longer treats a missing id as a failed referral.

## D4.2 [CONFIRMED · S2] Whole-table decoration flows make bulk sync quadratic

**Code:** `TBRepo.kt:135` (`allTbDiagnostics`), `:137` (`allTbScreening`); consumed by
`AllBenFragment.kt:807`, `NonHHFragment.kt:225`, `HouseholdMembersFragment.kt:232`,
`UnScreenedPeopleFragment.kt:152`.

Captured live during the TB pull:

| time | rows in TB_DIAGNOSTICS | emission |
|---|---|---|
| 11:54:04 | 610 | #3 |
| 11:55:03 | 3,110 | #8 |
| 11:55:52 | 7,110 | #16 |
| 11:56:39 | 11,110 | #24 |
| 11:57:28 | 15,110 | #32 |
| 11:58:19 | 19,110 | #40 |

**One emission per 500 rows written** (`pullWriteChunkSize = 500`), each re-reading the entire
table. Cost of ingesting N rows ≈ N²/1000 row-reads: ~400k at 20k, ~2.5M at 50k.

**Evidence of mechanism:** thread dumps at every stall show 3–4 threads inside
`SQLiteCursor.getCount → nativeExecuteForCursorWindow` with up to 18 parked in
`waitForConnection`. `journal_mode=wal` confirmed, so writers are not the blocker and the
transaction threads hold nothing.

**Impact at the extreme:** on an already-populated village this becomes a livelock — 4 Paging
refreshes started, **0 completed**, spinner still running after 4.5 minutes.

**Proposed fix:** key the decorations to the loaded page — request diagnostics/screening for the
benIds in the current `PagingData` window instead of observing the table.
**Rejected alternative:** bounding the flow to the selected village. It halves the rows but adds
`BENEFICIARY` to the flow's invalidation set, which is strictly worse during a beneficiary pull.

## D4.3 [CONFIRMED · S0] TB Screening reports success when nothing was saved

**Code:** `ui/.../tb_screening/form/TBScreeningFormViewModel.kt:158, 209-278`.

`tbRepo.saveTBScreening(tbScreeningCache)` is **commented out** at `:158`. The only write now
happens inside `initializeDiagnosticsAndPush()`, whose entire body is wrapped in
`catch (e: Exception) { Timber.e(…) }` at `:275-277` — it swallows and returns normally. The
caller then posts `State.SAVE_SUCCESS` unconditionally.

Any failure in `getBenFromId` (`:228`), `saveTBScreening` (`:237`) or `saveTBDiagnostics` (`:263`)
→ the user sees "saved", navigates away, **the screening was never written**. A back-press
mid-save hits the identical path, because `CancellationException` is an `Exception` in Kotlin.

## D4.4 [CONFIRMED · S0] Pushes mark records SYNCED that were never sent

**Code:** `TBRepo.kt:1507` (Suspected), `:1586` (Confirmed), `:1417` (Diagnostics).

The payload is built with `mapNotNull`, dropping records whose beneficiary cannot be resolved —
then the **whole original `chunk`** is passed to the mark-SYNCED helper. The code logs its own
defect at `:1505`:
> `"marking benIds=$excludedBenIds SYNCED even though they were NOT sent (benId resolution failed above) — this hides a real failure for those records"`

`:1586` has the same defect **without** the warning. At `:1417` the re-read guard in
`updateSyncStatusDiagnostics` (`:1662-1672`) finds the unsent row unchanged and flips it to SYNCED
too.

**Why it matters in camp mode:** a TB Suspected row routinely exists before its `BENEFICIARY` row
is pulled. It is dropped from the payload, marked SYNCED, and is then **invisible to every future
push cycle**.

## D4.5 [CONFIRMED · S0] Counselling records marked SYNCED with no API call
`CounsellingRepositoryImpl.kt:298-310, 414-427` — `submitSectionBulk` returns `true` on three
no-op paths, including `payload.sections.isEmpty()` (`:307-310`) which explicitly skips the call.
The caller treats `true` as confirmed and writes `syncStatus = "SYNCED"`.

## D4.6 [CONFIRMED · S0] Stale-snapshot write-back clobbers concurrent edits
`VitalRepo.kt:83-97`, `TBRepo.kt:1627-1635`, `:1691-1699`, `MalariaRepo.kt:220,245`.
Snapshot → network → write `snapshot.copy(syncState = SYNCED)` back. A nurse editing BP during an
in-flight push has the edit overwritten **and** marked SYNCED, so it is never re-pushed. `TBRepo`
fixed exactly this for two paths (`:1652-1689`) and left the rest.

## D4.7 [CONFIRMED · S0] Three independent routes to duplicate beneficiary registration
- `BenRepo.kt:489-490` — `getString("benGenId").toLong()` unguarded; a null/empty value throws,
  the broad catch at `:532` resets the row to UNSYNCED, and it is re-submitted although the
  server already created it.
- `BenRepo.kt:569, 584-588, 676-678` — rollback only runs when `postDataToAmritServer` *returns*
  false, but its catch at `:676` also catches `CancellationException`, so the rollback DAO calls
  run on a dead scope. Rows stay SYNCING forever.
- `SakhiApplication.kt:73-80, 116-137` — startup blanket-resets `SYNCING → UNSYNCED` across 14
  tables. Its comment asserts *"No sync can be in progress at app startup"*; WorkManager
  initialises in the same `onCreate`, so a rebooted push worker races it.

## D4.8 [CONFIRMED · S0] `CancellationException` swallowed app-wide
Only `TBRepo.kt:1321-1322, 1363-1364` rethrow. Swallowed at `TBRepo.kt:1288, 1438, 1530, 1607,
1998, 2360, 2444`; `BenRepo.kt:532, 676`; `BasePushWorker.kt:63`; both TB form ViewModels.

Worst case `TBSuspectedQuickViewModel.kt:275-574`: server-mutating calls run **first**, local
persistence last (`:573-575`, two non-transactional writes). Back-press between them leaves the
backend holding an order the device has no record of.

## D4.9 [CONFIRMED · S2] Main thread blocked on WorkManager's DB every 10 s
`WorkerUtils.kt:65-80` — a blocking `getWorkInfosForUniqueWork(name).get()`, called from a
`lifecycleScope` (= `Dispatchers.Main`) loop in `VolunteerActivity.kt:602-611` and
`HomeActivity.kt:592-600`, with no `repeatOnLifecycle` so it ticks while backgrounded. The same
path runs on **every save button** (12 call sites).
**Evidence:** `High input latency` counted **19,962** events over a 10,576-frame window while
GPU p99 was 10 ms — the UI thread is waiting, not drawing.

## D4.10 [STRONGLY SUPPORTED · S2] Dashboard issues 77 concurrent non-sargable Flow queries
`DashboardViewModel.kt:277-476` — 13 `collectBreakdown(...)` × 6 Flows (`:446-476`) + headcount,
all launched in `init{}` (`:173-175`), on `viewModelScope` (not `WhileSubscribed`), so they keep
re-running while the fragment is covered.

Every query is non-sargable by construction (`TBDao.kt:271-597`):
`UPPER(COALESCE(b.gender,''))`; `strftime('%s','now')` per row; three nested `REPLACE`s on
`:villageName` per row; a dead `AND (:endTime = :endTime)`. Five metrics are
UNION-of-two-full-scans. `getDashboardUnscreenedCount` (`:587-597`) is an un-indexable
`NOT IN (SELECT …)` anti-join, run 5×.

**Not Confirmed** because the Dashboard was never opened on the device.

## D4.11 [STRONGLY SUPPORTED · S2] Badge collectors leak a Room Flow per bind
`BindingUtils.kt:79-95` launches `scope.launch { flow.collect {} }` inside a binding adapter
called from `IconGridAdapter.onBindViewHolder:51-53` — no handle, no cancellation.
`NcdFragment.kt:53-56` / `CdFragment.kt:48-52` pass **`viewModelScope`**, so they outlive the view.
Two of those badges materialise whole tables to call `.size` (`RecordsRepo.kt:199-205`, `:164-171`
— the latter triggers an `@Relation` fan-out loading all CBAC rows, **twice**).

## D4.12 [CONFIRMED · S0/S3] The U+202F date fix landed in only 2 of 10 files
`ServerDateParser.kt` is referenced only from `TBRepo.kt` and `JsonAdapters.kt`.

| failure mode | files |
|---|---|
| **throws**, aborting the whole pulled batch | `AESRepo.kt:237`, `KalaAzarRepo.kt:253`, `LeprosyRepo.kt:665`, `MalariaRepo.kt:262`, `FilariaRepo.kt:409`, `BenRepo.kt:77` |
| **silently returns 0L** → server record always loses the recency comparison and is discarded | `VitalRepo.kt:352-365`, `BenRepo.kt:1223-1239` |

Also: every repo companion shares one mutable `SimpleDateFormat` used from concurrent IO
coroutines (`TBRepo.kt:1745`, `BenRepo.kt:69`, +5). Not thread-safe → garbled `createdDate` →
server rejects the chunk. `ServerDateParser` uses a `ThreadLocal` for the parse side only.

## D4.13 [CONFIRMED · S2] 2.6 s hard-coded splash delay
`SplashActivity.kt:43,156` — `postDelayed(…, 2600L)`, unconditional. Measured `am start -W`
TotalTime is 629–2,151 ms, so this roughly triples time-to-usable.

## D4.14 [CONFIRMED · S2] Glide caching deliberately disabled
`BindingUtils.kt:385-396` — `.signature(ObjectKey(currentTimeMillis()/1000))`,
`.skipMemoryCache(true)`, `.diskCacheStrategy(NONE)`, no `.override()`. Every bind re-decodes the
full-resolution JPEG, inside recycled form rows (5 layouts). Plus main-thread full-size decodes at
`FormRendererAdapter.kt:171-174, 183`, `FileListAdapter.kt:32`, `PreviewAdapter.kt:43`.

## D4.15 [CONFIRMED · S2] Forms built on the main thread; whole form stringified per keystroke
11 form ViewModels build their dataset as eager property initialisers (`NewBenRegViewModel.kt:126`
+10), each paying two `createConfigurationContext()` calls (`Dataset.kt:40-43`).
`Dataset.kt:186` — `Timber.d("Emitting ${newList}}")` is evaluated **eagerly**; `FormElement` is a
48-property data class, so this is a multi-kilobyte `toString()` on every keystroke, which then
runs `SyncLogTree`'s 13 substring scans and in release goes to disk.

## D4.16 [POTENTIAL RISK · S3] Stuck states with no recovery
`"CREATING"` order status written at `AllBenViewModel.kt:416,437,458` with no `finally`; the
string appears **only** at those three write sites and nothing resets it.
`TBRepo.kt:2163-2196` can strand `xrayManualResultPendingSync = true` with the poll worker no
longer rescheduling. `BasePushWorker.kt:60-69` converts every non-timeout error, including the
camp-hub `IOException`, into `Result.failure()` rather than `retry()`.

## D4.17 [CONFIRMED · S3] Unbounded recursive token-refresh retries
`BenRepo.kt:526-529`, `VitalRepo.kt:247,258,265`, `CbacRepo.kt:108-112` (which also discards the
recursive result and falls through on the stale error body). `TBRepo.kt:1224-1225` documents
removing exactly this pattern; the others never got it.

---

# Deliverable 5 — Performance baseline

**Environment:** as stated in the header. Monotonic timing via `SystemClock.elapsedRealtime()`
inside `ScreenLoadTrace`. Each figure is an individual run; medians stated where ≥3 runs.

## 5.1 Cold start (`am start -W`, 5 runs)
`689 · 1,988 · 629 · 2,151 · 677 ms` — median ~689 ms, bimodal. Plus 2.6 s splash (D4.13).

## 5.2 Screen first content — during active camp pull
| screen | runs | note |
|---|---|---|
| All Beneficiaries | 5,506 · 1,217 · 6,583 · 1,739 · 4,265 ms | 4 of 9 opens did not present within 12 s |
| Digital Chest X-ray | 7,634 ms | list never settled; 5 PagingSource recreations in 28 s |

## 5.3 Screen first content — steady state, nothing syncing
| screen | runs | median |
|---|---|---|
| All Beneficiaries (13.6k) | 8,864 (cold) · 1,184 · 1,184 · 1,183 · 1,071 · 1,113 · 1,008 | **~1,148 ms** |
| Digital Chest X-ray (19,110) | 4,068 · 6,015 · 6,541 · 3,930 | **~5,042 ms** |
| True NAT (6,296) | 5,180 · 5,371 · 3,224 | **5,180 ms** |

Paging refresh, 9 loads: `3,588 · 4,670 · 4,684 · 3,507 · 3,931 · 6,294 · 4,948 · 3,213 · 2,811`
— median **~3,931 ms**. DB stalls up to **6,692 ms**. 7 of 7 opens presented.

## 5.4 Query cost vs queueing — synthetic 9,987-row DB, `EXPLAIN QUERY PLAN` + `.timer`
| query | plan | time |
|---|---|---|
| `COUNT(*)` through `BEN_BASIC_CACHE` (what Paging runs) | `SCAN b` + `SEARCH h USING INTEGER PRIMARY KEY LEFT-JOIN` | **26 ms** |
| `COUNT(*)` straight off `BENEFICIARY` | index-eligible | **3 ms** |
| page 0 (`LIMIT 90`) through the view | — | **1 ms** |

A 26 ms query taking 3,030 ms on device = **~100× queueing inflation**.
`ind_ben_village_deactivate(loc_village_id, isDeactivate, isDraft)` already exists and is correct;
it is not reached because the COUNT goes through the view and still joins `HOUSEHOLD` per row.

## 5.5 Frame timing
| window | frames | janky | p50 | p90 | p95 | p99 | GPU p99 | high input latency |
|---|---|---|---|---|---|---|---|---|
| cumulative, during sync | 1,170 | 3.33% / 29.4% legacy | 10 ms | 57 ms | 77 ms | 350 ms | 10 ms | 2,135 |
| reset, steady state | 10,576 | **0.33%** / 5.43% legacy | 8 ms | 10 ms | 13 ms | **89 ms** | — | **19,962** |

## 5.6 API inventory — 434 calls / 68 s network over 9 min (login + full sync)
| endpoint | n | med | p95 | max | codes |
|---|---|---|---|---|---|
| `POST beneficiary/getBeneficiaryData` | 407 | 136 ms | 209 ms | 338 ms | 200 |
| `POST nurse/generalExamination/getAll` | 1 | **1,798 ms** | — | — | 200 |
| `POST nurse/tbScreening/getAll` | 1 | **1,727 ms** | — | — | 200 |
| `POST nurse/diagnostics/getAll` | 1 | **1,506 ms** | — | — | 200 |
| `GET diagnostic/vendor/health` | 6 | 117 ms | 812 ms | 812 ms | **401×4**, 200×2 |
| `GET order/getBeneficiariesByStatus` | 1 | 752 ms | — | — | 200 |
| `GET tm-api/master/doctor/masterData/…` | 1 | 355 ms | — | — | **401** |
| `POST common-api/user/refreshToken` | 3 | 60 ms | — | — | 200 |
| `GET flw-api/user/getUserDetail` | 1 | 58 ms | — | — | **202** |

Steady-state window (7 screen opens, no sync): **110 × `order/result`**, 8 ×
`getBeneficiariesByStatus`, 2 × `vendor/health` — i.e. ~16 result POSTs **per screen open**.

## 5.7 Memory / process
PSS 304 MB · Dalvik 36.5 MB (18.3 alloc) · native 67 MB · `.dex mmap` 44 MB (debug) ·
EGL+GL 121 MB · **92 threads**.

## 5.8 Sync throughput (already healthy)
`[pull] pages 115..136 (20 pages) wrote 1000 beneficiary rows in 931ms - one transaction, so 1
invalidation` — 20 transactions in 8.8 min ≈ one invalidation per 8.6 s. The beneficiary pull is
correctly batched; earlier blame on it was wrong.

## 5.9 Baselines NOT established
Release build; 50k/100k datasets; search latency (never exercised); scroll jank on a long list;
Dashboard; form open/typing; offline→online transition; process-death recovery.

---

# Deliverable 6 — Before/after comparison

Only the work already implemented in this branch has before/after data. **No fix from
Deliverable 4 has been implemented yet**, so there is nothing else to compare.

| change | metric | before | after |
|---|---|---|---|
| Bulk-resolve the order-id backfill + hoist the TB-Suspected push out of the per-record loop | `POST order/result` per screen open | 552 | 80 |
| ″ | `POST tb/suspected/saveAll` per screen open | 125 | 13 |
| Defer the 17 row-decoration collectors past first paint | flow emissions before first content | 16–20 | **1** |
| Narrow `allTbDiagnostics` 46 → 29 columns; delete the wide `SELECT *`; drop a duplicate `SELECT benId` scan | — | — | no isolated measurement; mechanism evidenced by thread dump |
| `MIGRATION_59_60` index on `benRegId` *(earlier session)* | Paging REFRESH during sync | 4,765 ms | 290–486 ms |

**Negative result, recorded:** deferring the *status sweep* past first paint was implemented,
measured (X-ray median 3,579 → 3,613 ms, markedly worse spread) and **reverted**. See D4.1 — that
test ran at ~960 members and does not hold at 19,110; it should be re-tested.

---

# Deliverable 7 — Fix inventory

**No fixes from this audit have been applied.** Changes already present on `hotfix/lagissue` from
the preceding work, for completeness:

| file | change | reason | risk |
|---|---|---|---|
| `TBDao.kt` | +3 bulk `getBenIdsWith{Xray,TrueNat,Rif}OrderId`; +narrow `getAllTbDiagnosticsCardFields`; −wide `getAllTbDiagnostics` | collapse per-record reads; cut cursor-window bytes | none identified; 65 tests pass |
| `BenDao.kt` | +`getExistingBenIds` | bulk fallback leg | none |
| `TBRepo.kt` | bulk bucket resolution; `fetchOrderResult(pushSuspectedAfter=)`; `orderIdBackfillPerSweep=50` | stop the 552/125 flood | backfill no longer converges — **D4.1** |
| `AllBenFragment.kt` | `startRowDecorationCollectors()` gated on first refresh settling | keep 17 whole-table reads off the critical path | bypassed on cold start when the DB is empty |
| `model/TbDiagnosticsCardFields.kt` | new 29-column projection | — | must stay in step with `BenListAdapter` |
| 4 list ViewModels | derive diagnostics ids from the already-collected list | remove a duplicate full scan | `ExamineViewModel` deliberately unchanged |

---

# Deliverable 8 — Worker and synchronization audit

**30 workers** under `work/`. No `PeriodicWorkRequest` anywhere; all cadence comes from two
`while(isActive)` activity loops (D4.9).

| worker | trigger | unique | concurrency | retry | finding |
|---|---|---|---|---|---|
| `PullFromAmritWorker` | 10 s loop | yes | sharded pages | — | ✅ batched, 1 invalidation / 1,000 rows |
| `PullTBFromAmritWorker` | 10 s loop | yes | `Semaphore(3)` over 9 domains | — | writes 500-row chunks → D4.2 |
| `DiagnosticResultPollWorker` | self, 60 s | yes | `Semaphore(4)` | — | full `getDiagnosticsList()` + ≤3 calls per pending beneficiary per cycle |
| `BasePushWorker` (all push) | save + loop | yes | — | 5 cap | **only `SocketTimeoutException` retries**; everything else `Result.failure()` — D4.16 |
| `pullAes/Filaria/Malaria` | 10 s loop | yes | unbounded `awaitAll` over 1–2 | — | harmless at current size |

**Concurrency findings:** `withBenIdLock` does not span the network call, so a check-then-create
sequence can still produce duplicate backend orders (D4.7). `CancellationException` is swallowed
in every push path (D4.8). The startup `SYNCING → UNSYNCED` reset races worker startup (D4.7).
No deadlock or lock-order inversion was found; the non-reentrancy of `withBenIdLock` is correctly
respected by the backfill, which runs outside it.

**Polling arithmetic (steady state, measured):** 7 screen opens produced 110 `order/result` POSTs
— ~16/open. With a 19,110 awaiting bucket and a 50/sweep cap, full convergence needs ~382 sweeps.
A 30 s debounce on the unfiltered refresh bounds this to ~2 sweeps/min while a user works.

---

# Deliverable 9 — Edge-case matrix

| scenario | status | result |
|---|---|---|
| Open the same card repeatedly | **Tested** | 4–8 opens per screen, consistent; no leak observed in `dumpsys meminfo` |
| Reopen after DB changes | **Tested** | reload on every pull batch; refresh grows 308 → 3,030 ms as the table fills |
| Open a screen while a worker writes | **Tested** | the core failure mode; livelock observed on X-ray |
| Rapid tab switching | **Not tested** | — |
| Navigate away mid-request | **Not tested on device** | code review shows cancellation swallowed (D4.8) |
| Process recreation | **Not tested** | — |
| Offline registration → reconnect | **Not tested** | — |
| Network loss mid-request | **Not tested** | `CampModeUrlInterceptor` throws a message no UI handler matches (`AllBenFragment.kt:737-748`) |
| Expired token / refresh | **Observed incidentally** | 5 × 401 drove 3 refreshes; `vendor/health` 401×4 unexplained |
| Two workers, same beneficiary | **Not tested** | single device |
| Temp ID → server ID swap | **Not tested** | `BenRepo.kt:493-499` rewrites it mid-push; a stale nav-arg then `!!`-crashes (`NewBenRegViewModel.kt:193`) |
| DB migration with pending sync | **Not tested** | `MigrationTestHelper` not run |
| Empty / loading / error states | **Partially** | 33 of 40 ViewModels have no error channel at all |
| Search: exact / partial / empty / special chars | **NOT TESTED** | search was never exercised — a gap against the brief |
| Large result sets (50k/100k) | **NOT TESTED** | max real dataset 20k |

---

# Deliverable 10 — `CLAUDE.md` review

**No changes made yet.** Proposed, with evidence:

| current guidance | verdict | reason |
|---|---|---|
| "Don't silently fix the dual push-path race; it may be relied upon elsewhere" | **Revise** | D4.4/D4.7 show it is actively losing and duplicating patient records. Replace with: the race is a known defect; changes require a documented contract and tests. |
| "Never change interceptor chain order in `di/AppModule.kt`" | **Revise** | D11.2 (security) *requires* reordering — `tokenInsertTmcInterceptor` must run **after** `campModeUrlInterceptor`. Replace the blanket ban with the rationale and the one correct order. |
| "Never change `CampModeUrlInterceptor` logic" | **Revise** | Same finding. It must gate on `isCampModeEnabled()`. |
| "Never use `fallbackToDestructiveMigration` as a fix" | **Keep** | Genuine data-safety rule. |
| "Bumping DB version REQUIRES a matching Migration" | **Keep** | Genuine. |
| "Never commit secrets / never set `IS_DEVELOPMENT=1`" | **Keep and strengthen** | D11.1 shows the file is tracked, so the rule needs a pre-commit guard, not just prose. |
| "Don't change `AmritApiService`/`AbhaApiService` signatures" | **Keep** | Backend contract. |
| Server page size "10" (`BenRepo.kt:63-65`, `:929-930`) | **Correct** | It is 50. |
| `proguard-rules.pro:124-134` "release logs never go to logcat" | **Correct** | Contradicted by `SyncLogTree.kt:58-60`. |

**New rules the evidence justifies adding:**
1. No `catch (e: Exception)` in a repository or worker without rethrowing `CancellationException`
   first.
2. No whole-table `Flow<List<Entity>>` over a synced table for row decoration (the D4.2 N² trap).
3. Mark records SYNCED only for rows actually accepted by the server.

---

# Deliverable 11 — Remaining work, ranked

| # | item | severity | confidence | blocked on |
|---|---|---|---|---|
| 1 | TB Screening reports success without saving (D4.3) | S0 | Confirmed | — |
| 2 | Pushes mark unsent records SYNCED, 3 sites (D4.4) | S0 | Confirmed | — |
| 3 | `native-lib.cpp` git-tracked with secrets (D11.1 below) | S1 | Confirmed | history rewrite + secret rotation |
| 4 | QR redirects JWT + refresh token (D11.2 below) | S1 | Confirmed | product decision on allow-list |
| 5 | Counselling marked SYNCED with no API call (D4.5) | S0 | Confirmed | — |
| 6 | Status sweep blocks every list open; backfill never converges (D4.1) | S2 | Confirmed | — |
| 7 | Whole-table decoration flows → N² (D4.2) | S2 | Confirmed | touches 5 screens + shared adapters |
| 8 | Stale-snapshot write-back (D4.6) | S0 | Confirmed | — |
| 9 | Duplicate registrations, 3 routes (D4.7) | S0 | Confirmed | — |
| 10 | Cancellation swallowed app-wide (D4.8) | S0 | Confirmed | — |
| 11 | Blocking `.get()` on main thread every 10 s (D4.9) | S2 | Confirmed | — |
| 12 | U+202F date bug in 8 repos (D4.12) | S0 | Confirmed | — |
| 13 | Dashboard 77 flows (D4.10) | S2 | Strongly supported | needs device measurement first |
| 14 | 2.6 s splash delay (D4.13) | S2 | Confirmed | product sign-off on branding time |
| 15 | Badge collector leak (D4.11) | S2 | Strongly supported | — |
| 16 | Glide caching disabled + main-thread decodes (D4.14) | S2 | Confirmed | — |
| 17 | Forms on main thread; per-keystroke `toString()` (D4.15) | S2 | Confirmed | — |
| 18 | Logging leaks beneficiary IDs to disk in release (D11.3) | S1 | Confirmed | — |
| 19 | Cleartext internet-wide; no cert pinning | S1 | Confirmed | backend pin set |
| 20 | `CryptoUtil` non-CSPRNG IV/salt, 1989 iterations | S1 | Confirmed | re-encryption migration |
| 21 | Stuck states: `CREATING`, pending manual result, `BasePushWorker` (D4.16) | S3 | Potential risk | — |
| 22 | Recursive token-refresh retries (D4.17) | S3 | Confirmed | — |

**Requires resources I do not have:** release-build measurement (signing config); 50k/100k stress
data (isolated backend); multi-device concurrency; backend-side confirmation of the `vendor/health`
401s and the `getUserDetail` 202.

## D11.1 [CRITICAL] `native-lib.cpp` is git-tracked
Listed in `.gitignore:38` and `:93`, but **gitignore does not apply to already-tracked files** —
`git ls-files` lists it, `git check-ignore` returns non-zero, and it is currently modified with
sandbox secret values. Any `git add -A` commits them. Same for `res/raw/wildcard.crt`.

## D11.2 [CRITICAL] Unvalidated QR camp-hub URL redirects authenticated traffic
`CampModeConnectFragment.kt:53-63` takes raw QR contents; `CampModeConnectViewModel.kt:68-76`
only prepends `http://`; `:50` persists before any reachability check;
`AppModule.kt:114-116` attaches `Jwttoken`/`userId` **before** `CampModeUrlInterceptor.kt:35-39`
rewrites scheme/host/port on every request. The interceptor never checks `isCampModeEnabled()`
(`:20-26`) and the URL survives a failed ping (`CampModeConnectViewModel.kt:51,58`). The **auth
client carries it too** (`AppModule.kt:76`), so the refresh-token POST also goes to the attacker
host. `network_security_config.xml:3` permits cleartext to every host.

## D11.3 [HIGH] Logging persists beneficiary identifiers in release
`AppModule.kt:142-155` is `BASIC` with **no `BuildConfig.DEBUG` gate**, and `BASIC` logs full
URLs — `order/result?beneficiaryId=…&orderType=…` and four more endpoints carry identifiers in
the query string. `SyncLogTree` is planted unconditionally (`SakhiApplication.kt:57`), matches on
`"amrit"`/`"beneficiary"`, writes to `filesDir/sync-logs/` for 3 days / 100 MB, re-emits to
logcat in release (`:58-60`), and `SyncLogExporter.kt:26-56` ships the lot to any `ACTION_SEND`
target.

---

# Appendix — Skills and tooling actually used

| tool | applied to | executed | output | limits |
|---|---|---|---|---|
| `android-performance-audit` | methodology for §D5 | ✅ | baseline protocol, cold-start + gfxinfo commands | — |
| `room-sqlite-optimization` | §5.4 query plans | ✅ | `EXPLAIN QUERY PLAN` + `.timer` on a synthetic 9,987-row DB via on-device `sqlite3` | synthetic DB, not the live one (PII) |
| `recyclerview-paging-performance` | §D4.2, PagingConfig review | ✅ | placeholder/diff/bind checklist applied | no scroll-jank test run |
| `security-pii-audit` | §D11 | ✅ | full checklist, 2 Critical + 4 High | read-only |
| `release-readiness-check` | Deliverable 10 / §7 | ✅ | 8-item table | release build not compiled |
| `adb-device-debugging` | all device capture | ✅ | logcat, dumpsys, `am start -W` | — |
| `android-architecture-review` | Deliverable 2 | partial | execution map traced by hand | — |
| `performance-rca-report` | this document's shape | ✅ | — | — |
| `android-regression-testing` | Deliverable 9 | ❌ **not run** | — | needs the fixes in place first |
| `gradle-build-troubleshooting` | — | not needed | — | builds were clean |
| Perfetto / Macrobenchmark / LeakCanary / StrictMode / Studio Profiler | — | ❌ **not run** | — | no memory-leak or CPU-slice claim is made anywhere in this report |

**Custom instrumentation used** (temporary, on this branch): `ScreenLoadTrace`,
`PerfTimingInterceptor`, `TimingPagingSource`, and `DbContentionSampler` — extended during this
audit to dump every Room thread's top frames on any stall over 1 s, which is what identified the
connection-pool saturation in D4.1/D4.2.
