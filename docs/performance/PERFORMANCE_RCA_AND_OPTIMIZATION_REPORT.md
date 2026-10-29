# STOP-TB — Performance RCA and Optimization Report

**Device:** LENOVO TB336ZU (`HNY0NEBK`), Android 16 / API 36, 7.8 GB RAM
**Build:** `stoptbUatDebug`, branch `hotfix/lagissue`
**Dataset:** village Ambiliambaguda (11821) — 9,987 beneficiaries, 9,987 TB screening rows,
9,987 diagnostics rows, 2,497 suspected rows. DB file 10.6 MB.
**Status:** 5 root causes fixed and verified on device. 1 remains, precisely located, not fixed.

---

## A. Executive summary

The reported problem was "very very slow loading" — opening cards and tabs, and searching
beneficiaries. The worst case was not slow but **non-terminating**: the All Beneficiaries list
sat on a spinner indefinitely while the sync ran.

Seven distinct causes were found, each proven with on-device measurement rather than inspection.
Six are fixed. Two false leads were chased and measured away before the sixth was found — both are
written up (§E3, §E4), because what did *not* work is part of the evidence:

| # | Cause | Evidence | Status |
|---|---|---|---|
| 1 | Server dates carry U+202F before AM/PM; every pattern uses an ASCII space, so 70,000 of ~95,000 date strings per pull failed to parse — ~500k thrown exceptions per cycle, and an unparseable `updateDate` became `0L`, which `shouldApplyServerRecord` reads as "apply", rewriting every row on every pull | 636 parse failures / 12 s idle; 23.7 MB/s allocation, 12 GCs / 18 s, heap pinned 160/160 MB at 0% free | **Fixed** |
| 2 | `fetchBeneficiariesByStatus` fanned out one coroutine per completed beneficiary (5,500 of them), unbounded | 2,664 jobs cancelled on one back-press → UI frozen 1,657 ms, 180 frames dropped | **Fixed** |
| 3 | Four of five `updateSyncStatus*` push writers committed one row per transaction | a 20-row push chunk → 20 Room invalidations | **Fixed** |
| 4 | `BEN_BASIC_CACHE` carried correlated subqueries into CBAC / TB_SCREENING / TB_SUSPECTED, so any TB write invalidated every beneficiary list | 83 PagingSource generations in 171 s, list never settled | **Fixed** |
| 5 | `searchBenPaged` names TB_SUSPECTED / TB_DIAGNOSTICS / TB_SCREENING / BEN_VITALS in its `:source = 5..10` branches; Room derives observed tables from SQL text, not from the branch taken, so All Beneficiaries was invalidated by TB writes it can never depend on | generated `BenDao_Impl` observed-table list | **Fixed** |
| 6 | **`BENEFICIARY.benRegId` had no index**, yet every pull resolves each incoming record through it — a full scan of 9,987 wide rows per record, across 11 call sites. This, not the observer flows, is what made the app slow while syncing | Sync running: first content median **4,394 ms** → **831 ms**; Paging REFRESH **4,765 ms** → **290–486 ms**; pull worker stage **175 s** → **100 s** | **Fixed** (`MIGRATION_59_60`) |
| 7 | Eager observer flows build 9,987-element collections on every screen open (`allTbDiagnostics` is 9,987 × 46 columns), exhausting the heap | At open #7: **0% free, 77 MB / 77 MB**, one GC freeing 49 MB incl. 16 MB large objects; 4 of 10 opens failed to present *after* the database was no longer the constraint | **Open** |

---

## C. Root-cause analysis

### C1. The narrow no-break space (U+202F)

Raw bytes from the device log:

```
'Sep 29, 2026, 3:34:00 PM'
                      ^^^^^^ U+202F NARROW NO-BREAK SPACE
```

The backend echoes dates formatted by a CLDR 42 / ICU 72+ runtime, which emits U+202F before
AM/PM. Every pattern in the app spells that separator as an ASCII space, and `SimpleDateFormat`
matches pattern literals exactly. Counted across one captured pull:

| field | U+202F (failed) | plain (parsed) |
|---|---|---|
| visitDate | 20,000 | 2,500 |
| updateDate | 30,000 | 12,550 |
| createdDate | 20,000 | 10,050 |

Each failure built up to 10 `SimpleDateFormat` instances and threw up to 10 `ParseException`s,
each capturing a stack trace. Two consequences: allocation churn, and silent sync corruption —
`parseServerUpdateDate` returned `0L`, and `shouldApplyServerRecord` treats `0L` as "no server
timestamp, apply it", so every record was rewritten on every pull and `serverUpdatedDate` never
converged.

### C2. Unbounded fan-out in `fetchBeneficiariesByStatus`

`getBeneficiariesByStatus` returned `completed: 4000` (XRAY_CHEST) + `1500` (MTB). The code ran
`completedList.map { async { ... } }` — one live coroutine per beneficiary. The comment there
argued OkHttp's 5-per-host cap made this safe; that cap is what made it unsafe, because the other
~5,495 coroutines queued behind a 5-wide pipe holding their Room reads alive, draining at 42/s
(~130 s). Leaving the screen cancelled 2,664 of them at once.

### C3–C5. The invalidation chain

Three separate links, all required:

1. `updateSyncStatus*` committed per row → 20 invalidations per push chunk.
2. The view read CBAC / TB_SCREENING / TB_SUSPECTED → any TB write invalidated every list.
3. The query named TB tables in untaken branches → still observed after (2) was fixed.

With the sync writing every ~2 s and a refresh taking longer than that, every refresh was
cancelled before completing. That is why no `REFRESH` line ever appeared in the trace and why the
screen showed a spinner forever — not slowness, non-termination.

### C6. Executor starvation — it is the sync, and only the sync

With invalidation fixed, one refresh per screen open completes. What remained was a refresh that
took 2.2–3.8 s for a query costing **54 ms** on the same device, with a no-op `SELECT 1` sampled
off the load path waiting a median of 2,354 ms.

The suspicion was that the eager observer flows were starving the list. **That was wrong.**
Running the same screen with the device offline and the identical build settles it:

| Device state | First content, 10 opens | Opens that reached content |
|---|---|---|
| Offline (no sync possible) | 690 / **median 772** / 1,064 ms | 10 / 10 |
| Sync running | 4,182 / **median 4,394** / 6,116 ms | 5 / 10 |

Same code, same 9,987-row dataset, same device. **The list is 5–6× slower while a sync runs, and
half the opens never present at all.** Offline, the same no-op probe returns in 84–244 ms; during
sync it waits up to **5,423 ms**.

The flows are not the cause. Across ten opens during a sync there were only 18 flow re-emissions
in total, so this is not invalidation churn either — the pull simply occupies the database. Two
things make it continuous rather than occasional:

- `PullTBFromAmritWorker` launches **nine domain pulls concurrently** in one `awaitAll` —
  general examination, TB screening, general OPD, diagnostics, suspected, confirmed, counselling,
  contact/TPT follow-up, visit categories — each reading and writing the same database. One
  observed run took **341 s** (175 s in the worker stage), with the beneficiary pull's 200 pages
  running alongside it.
- `HomeActivity`'s 30-second auto-pull loop re-fires throughout. It logged
  `Pull [AUTO_PULL] skipped: existing unique pull chain is already active` **24 times** in a single
  five-minute capture, because the previous chain had not finished.

So on a camp device the sync is not a background event the user occasionally collides with; it is
close to the steady state, and the list is slow for as long as it lasts.

`vendor/health` was also measured again at **5,049 ms** for a single call.

---

## D. Fixes implemented

| File | Change |
|---|---|
| `utils/ServerDateParser.kt` *(new)* | Normalises U+202F/U+00A0, caches formatters per thread, fails via `ParsePosition` instead of throwing. Preserves the old prefix-match rule so pattern-list ordering behaves identically. |
| `repositories/TBRepo.kt` | All three date parsers routed through it; missing comma-after-year patterns added; `completedList` chunked at 16; `CancellationException` rethrown in `fetchOrderResult` and `fetchBeneficiariesByStatus`; `updateSyncStatusScreening/Diagnostics/Suspected/Confirmed` commit once per chunk, taking per-beneficiary locks *before* the transaction to preserve the existing global lock ordering. |
| `network/JsonAdapters.kt` | Same two parsers. `getLongFromDate` previously threw on an unparseable date; it now returns `0L`, consistent with its own null handling. |
| `model/Ben.kt` | Six correlated subqueries removed from `BEN_BASIC_CACHE`; the columns are literals, matching the dozen placeholders already there. Nothing in Kotlin ever read them. |
| `database/room/InAppDb.kt` | Version 58 → 59 and `MIGRATION_58_59`, a view-only drop/recreate. CREATE VIEW text copied verbatim from the generated impl and asserted equal to it. |
| `database/room/dao/BenDao.kt` | New `searchBenPagedWithoutTbTables` for sources 0–4; five predicates that used the removed columns now test `TB_SCREENING` directly (two were redundant with an existing inner join). |
| `repositories/RecordsRepo.kt` | Sources 0–4 routed to the TB-free query. |
| `ui/.../AllBenViewModel.kt` | `distinctUntilChanged()` on `diagnosisBenIds` — `combine` emits once per emission of *either* source, so one logical change was delivered twice. |
| `adapters/BenListAdapter.kt` | `tbDiagnosticsList.find { it.benId == ... }` — a linear scan of 9,987 entities run twice per row bind on the main thread — replaced with a benId-keyed map. |
| `utils/DbContentionSampler.kt` *(new, temporary)* | Samples a no-op query off the load path to separate executor queueing from query cost. |

### Design alternatives considered

- **Batch the SYNCED marking across all chunks** instead of per chunk: one invalidation instead of
  ~57, but widens the window where a crash loses the marks and re-pushes rows, and touches the
  documented dual push-path race. Rejected in favour of per-chunk transactions.
- **Drop the six view columns from `BenBasicCache` entirely** rather than making them literals:
  larger diff, no benefit — Room maps them fine and nothing reads them.
- **Inline executor probe inside `PagingSource.load()`**: implemented, then reverted — see F.
| `model/TbScreeningSymptoms.kt` *(new)* + `TBDao.getAllTbScreeningSymptoms` | `allTbScreening` projects 12 columns instead of `SELECT *` over 50, dropping four TypeConverter list parses per row. `TBScreeningDataset.isPresumptive` gained an overload for the projection; both overloads delegate to one private `decide()` so the rule cannot drift. |
| `adapters/BenPagingAdapter.kt` | Now builds and passes `tbDiagnosticsByBenId`. The unread `tbDiagnosticsList` parameter was removed from `BenListAdapter.bind()` so a missing argument is a compile error, not an empty default. See §E2. |
| `app/src/test/.../IsPresumptiveProjectionTest.kt` *(new)* | 3 tests, 350 comparisons, asserting the projection and the full row produce the same label for every answer/asymptomatic combination. |


---

## E. Before/after measurements (device, same dataset)

| Metric | Before | After |
|---|---|---|
| All Beneficiaries, first content | 5,252 ms | 1,312 ms |
| Date-parse failures | 636 / 12 s | 0 |
| Allocation rate | 23.7 MB/s | 7.3 MB/s |
| GC wall time | 58% of window | 6% |
| Min heap free | 0% (160/160 MB) | 27% (peak 86 MB) |
| Worst GC pause | 1,904 ms | 393 ms |
| Back-press freeze (X-ray list) | 1,657 ms, 180 frames dropped | 64 ms, 0 dropped |
| Cancellation log entries on back | 2,664 | 0 |
| `diagnosisBenIds` emissions | 93 / 171 s | 1 / 31 s |
| Invalidations per push chunk | 20 | 1 |
| PagingSource generations | 83 / 171 s, never settled | 10 per 10 opens, no restarts |
| TB Suspected pull replay | 2.45 s, 5,505 commits | 0.02 s, 12 commits, identical content hash |

### The TB_SCREENING projection, measured properly

`allTbScreening` observed `SELECT * FROM TB_SCREENING` — 50 columns per beneficiary, four of them
`List<>` columns costing a TypeConverter parse per row — to render one *Presumptive / Asymptomatic*
label. It now observes a 12-column projection. Measured as a true A/B: same device, offline, 10
opens each, the only difference being the query.

| Metric (offline, n=10) | `SELECT *` | 12-column projection |
|---|---|---|
| `allTbScreening` ready | median **748 ms** (529–1,181) | median **440 ms** (392–714) |
| `allTbDiagnostics` ready *(control, untouched)* | median 769 ms | median 757 ms |
| First content | median 744 ms | median 772 ms |
| GC during the run | 234 MB over 11 collections | 182 MB over 9 collections |

The flow it targets is **41% faster** and the untouched control flow did not move, which is the
result one wants from a change like this. **First content did not improve** — it is not gated on
that flow — so this change should be credited with reducing database and allocation load, not with
making the screen open faster.

**Unmeasured:** p90/p95 — sample sizes here are 10 opens per configuration, not enough to quote
percentiles honestly. Cold-vs-warm was not separated. 50k/100k/1M dataset scaling was not run.
The X-Ray/TrueNat card fix below is verified by code inspection and compilation only: every
beneficiary in this device's dataset has null diagnostic results, so there is no visible
before/after to screenshot.

---

## E2. A regression I introduced, and the fix

Replacing `tbDiagnosticsList.find { it.benId == item.benId }` with an O(1) map lookup in
`BenListAdapter.bind()` was correct in itself — that scan ran twice per row bind over 9,987
entities on the main thread. But `bind()` is shared with **`BenPagingAdapter`**, which is the
adapter All Beneficiaries and Non-HH actually use, and that adapter kept passing only the flat
`tbDiagnosticsList`. The new `tbDiagnosticsByBenId` parameter therefore defaulted to `emptyMap()`
on both paging screens, so `tbDiagForStatus` was always null and every card rendered Chest X-Ray
and TrueNat as "Unscreened" with a blank result, whatever the database held. The order-status
blocks for sources 6–8 read the same null.

It compiled and it was silent, because the parameter had a default.

Fixed by giving `BenPagingAdapter` the same map, keeping it in step inside `submitTBDiagnostics`,
and **removing the now-unread `tbDiagnosticsList` parameter from `bind()` entirely** so the two
adapters cannot drift apart this way again — a missing argument is now a compile error rather than
an empty default.

---

## E3. Bounding the sync fan-out — tried, measured, did not work

With sign-off, `PullTBFromAmritWorker`'s nine concurrent domain pulls were bounded to three at a
time with a `Semaphore`, matching the existing `MAX_CONCURRENT_POLLS` idiom in
`DiagnosticResultPollWorker`. Measured the same way, 10 opens with a sync running:

| | Nine at once | Bounded to three |
|---|---|---|
| First content | 4,182 – 6,116 ms, median 4,394 | 3,761 – 6,234 ms, median 5,040 |
| Opens that presented | 5 / 10 | 5 / 10 |
| Worst no-op probe wait | 5,423 ms | 6,126 ms |

**No improvement.** The bound itself works — only four of the nine domain endpoints were called
during the capture, exactly as a limit of three predicts — so the fan-out was simply not the
constraint.

What the capture shows instead: the whole run made **6 API calls**, yet the pull occupied the
database for minutes. The time is not network and not concurrency; it is the volume of individual
statements each domain issues on its own. `saveTBScreeningCacheFromNewResponse` is representative
— inside a write transaction, **per record**:

```kotlin
val ben      = benDao.getBenByRegId(benRegId) ?: continue   // 1 SELECT
val existing = tbDao.getTbScreening(ben.beneficiaryId)      // 1 SELECT
...
tbDao.saveTbScreening(cache)                                // 1 INSERT OR REPLACE
```

At ~10,000 records that is **~30,000 statements for one domain**, and the same shape repeats
across all nine. Limiting how many run at once cannot help, because even one is enough to saturate
the database for the length of a pull. The real lever is per-chunk bulk reads — one
`getBenByRegId`-style lookup and one existing-row lookup per chunk instead of per row — which
would cut three statements per record to roughly two per chunk plus the writes.

The semaphore has been left in place as a bound on worst-case concurrency against a 4-thread
transaction executor, but it should not be credited with any latency improvement: there is none.

---

## E4. The sync bottleneck was a missing index

Bulk reads were applied to the TB screening save path (per-chunk `IN (:ids)` lookups and one
batched insert, replacing three statements per record). Measured with a sync running: **no
improvement**, 2,763–6,484 ms. The capture showed why — the semaphore limits the pull to three
domains at a time, and only one of those three was the path that had been changed. The other two
still did the same per-record work.

Tracing that work to its root: every domain resolves an incoming record to a beneficiary with

```sql
SELECT * FROM BENEFICIARY WHERE benRegId = ? LIMIT 1
```

`BENEFICIARY`'s primary key is `beneficiaryId`. **`benRegId` carried no index at all.** Every one
of those lookups was a full scan of 9,987 wide rows to read a single id, and the pull runs one per
incoming record across **eleven call sites** in `TBRepo`, `VitalRepo` and `BenRepo`. At ~10,000
records per domain that is on the order of 10⁸ row reads per domain pull.

Fixed by adding the index — `MIGRATION_59_60`, version 59 → 60, `CREATE INDEX IF NOT EXISTS
ind_ben_reg_id ON BENEFICIARY (benRegId)`, asserted byte-identical to Room's generated statement
and applied on-device with no destructive fallback. No call site changed.

| 10 opens, sync running | Before | After index |
|---|---|---|
| **Paging REFRESH** | 4,765 ms | **290 – 486 ms** |
| First content | 4,182 – 6,116 ms, median 4,394 | **778 – 1,244 ms**, median 831 |
| Pull worker stage | 175 s | **100 s** |
| Domains completing in the window | 3 | all 9, plus order polling |

The list during a sync is now close to its offline floor of 772 ms.

### What still fails, and why it is a different problem

Four of ten opens still did not present — but **not because of the database**. At open #7 the heap
was at **0% free, 77 MB / 77 MB**, with a collection freeing 49 MB including 16 MB of large-object
allocations. Those are the 9,987-element collections the eager observer flows build on every
screen open, `allTbDiagnostics` worst among them at 9,987 rows × 46 columns. The paging query is
no longer starved; the process is simply out of memory when it runs.

That makes the `allTbDiagnostics` projection (§I) a memory fix with measured justification, rather
than the speculative latency fix it would have been earlier.

---

## E5. TrueNat/X-Ray list changes, measured to 25k beneficiaries

Two functional changes were made to the diagnostics lists (an order the backend holds now makes a
beneficiary a member, and a placed-but-unresolved order gets its own ordering tier above "no
order"). Both touch `searchBenPaged`'s source 6/7 branches, which carry correlated subqueries, so
they were measured against a schema-identical synthetic database built from Room's own generated
DDL - 10,000 and 25,000 beneficiaries, no live data.

Plan first, because it is the part that decides how this scales. `EXPLAIN QUERY PLAN` is
**identical** before and after:

| | old | new |
|---|---|---|
| correlated (per-row) subqueries | 6 | **6** |
| list subqueries (once per query) | 12 | **12** |
| index seeks | 14 | **14** |

The membership clause extends an existing **uncorrelated** `LIST SUBQUERY` (SQLite materialises it
once with a bloom filter), and the tier change wraps the existing subquery in `IFNULL` inside a
*simple* `CASE`, which evaluates its subject once. So no new per-row work exists to scale.

Measured on the device (motorola edge 20, arm64, sqlite 3.45.1), COUNT + first page:

| dataset | old | new | delta | members listed |
|---|---|---|---|---|
| 10,000 beneficiaries | 88 ms | 92 ms | **+4 ms** | 4,596 → 5,210 |
| 25,000 beneficiaries | 188 ms | 196 ms | **+8 ms** | 10,596 → 11,210 |

The card's `getTrueNatBenCount` badge, changed to match, costs **+1 ms** at both sizes.

The extra time tracks the 614 extra rows, not extra work per row: per-row cost is 4.00 µs before
and 3.92 µs after. Scaling from 10k to 25k is linear in both variants.

Ordering, verified on the 10k set - position in the list of the first beneficiary in each state:

| state | old | new |
|---|---|---|
| FAILED / MANUAL_ENTRY | 1 | 1 |
| **PENDING (order in flight)** | **2,920** | **301** |
| no order at all | 120 | 801 |
| COMPLETED | 3,532 | 4,011 |
| CLOSED | 4,532 | 5,011 |

On the real device the True NAT badge went 75 → 80: five beneficiaries holding an order with no
local clinical evidence, previously absent from the list entirely.

**A harness bug worth recording.** The first run of this benchmark reported +1.3 ms and an
ordering that did not change. Both were wrong: `searchBenPaged` binds `source` to placeholders
2-12 *and* 30-35, and the harness had bound 30-35 as `query`. With `source` empty the ORDER BY's
`WHEN ? = 6 / = 7` never matched, so the correlated subqueries never ran and the tier collapsed to
a constant. The numbers above are from the corrected harness, which asserts the bind map taken
from the generated `BenDao_Impl`.

---

## F. A fix that was wrong, and what it cost

To split "slow query" from "busy executor", a no-op Room query was timed either side of
`PagingSource.load()`. It stopped the delegate's REFRESH from running at all — Paging delivered an
empty `PagingData` and the list never populated. The symptom looked exactly like the bug under
investigation, and for one test cycle it was wrongly attributed to the view change. Reverted; the
sampler now runs off the load path. **Do not issue a Room query from inside a `PagingSource.load`.**

Separately, an early check reported the migrated view "STILL READS TB/CBAC". That was a bad test —
SQLite `LIKE` is case-insensitive, so `'%CBAC%'` matched the column alias `cbacFilled`.

---

## G. Correctness verification

- **Query equivalence:** old vs new paged query compared on-device across 5 sources × 4 search
  terms, for both set membership and row order (first 300 rows) — **40/40 identical**, 0 rows
  missing, 0 extra, 0 order differences.
- **Migration safety:** migration SQL asserted byte-identical to Room's expected view text, applied
  to a replica first, then to the device. No destructive fallback, no migration warning, DB still
  10.6 MB, all 9,987 records intact.
- **Date parsing:** 7 unit tests on verbatim captured strings, including assertions that the *old*
  logic returns null where the new one succeeds, and that both agree on every string the old one
  handled.
- **Pull replay:** the real 5,505-record TB Suspected payload replayed through both code paths —
  identical content hash `8f877c89d4b26bf3`, same 5,505 rows.
- **Unit suite:** 62 tests, 0 failures.

---

## H. Device verification

sqlite3 3.45.1 was cross-compiled for arm64 with the project's NDK and pushed to
`/data/local/tmp/sqlite3` (Android user builds have not shipped one since API 28). SELinux blocks
executing it inside the app sandbox, so query timing was done against a schema-identical synthetic
replica — same device CPU, same indices, 9,987 rows, 7.2 MB vs the real 10.6 MB. **No live
beneficiary data was copied anywhere.**

Device artefacts left behind: `/data/local/tmp/{sqlite3,perf.db,migtest.db,*.sql}` — all synthetic.

---

## H2. Camp hub, 10k+ beneficiaries — what a live camp changed

Everything in §A–§E was measured with the device pulling from the UAT backend. This section is a
second pass taken against a **local camp hub** (`Camp Hub Connected`, hub at `172.18.0.157:8080`)
ingesting a full village, on the Lenovo TB336ZU. It changed the picture, found a regression I had
introduced, and ended with the real bottleneck named by a thread dump rather than inferred.

Dataset note up front, because it limits what can be concluded: the camp pull was **still
ingesting throughout**. The selected village grew 1,950 → 9,950 → 10,700 → 12,700 → 13,600
beneficiaries across the runs below. Run-to-run "first content" numbers are therefore **not
comparable to each other** — each later run faced both more rows and a busier writer. The
findings that do hold are the ones measured *within* a single run: call counts, emission counts,
and the thread dump.

### H2.1 Opening the X-ray list triggered the work that then blocked it

First instrumented open of **Digital Chest X-ray** (9,987 beneficiaries, camp hub connected):

```
[Digital Chest X-ray] FIRST CONTENT DRAWN (9987 items)  ===> TOTAL 7634ms <===
```

Split:

| phase | time |
|---|---|
| tap → ViewModel, layout, collectors wired | 113 ms |
| first PagingData reached the adapter | 104 ms |
| **waiting for Paging's refresh to return rows** | **7,284 ms** |

So 7.2 s of 7.6 s was spent blocked on the database, and the list never settled afterwards:
`NEW PagingSource (source=6)` was re-created at +3.6 s, +10.5 s, +13.0 s, +25.2 s and +27.9 s, and
`allTbDiagnostics` re-emitted all 9,987 rows **seven times in 28 seconds**. Alongside it,
`[db] executor made a no-op query wait 3534ms`, then 2,301 ms, then 2,650 ms — a `SELECT 1` that
does no work, waiting seconds.

The API mix for that one screen open explained the writes:

```
 552  POST /flw-api/diagnostic/order/result
 125  POST /flw-api/tb/suspected/saveAll
   2  GET  /flw-api/diagnostic/order/getBeneficiariesByStatus
```

**Two `getBeneficiariesByStatus` sweeps produced 552 result fetches and 125 full TB-Suspected
pushes.** That is the screen invalidating the very query it is waiting on, several hundred times.

### H2.2 Root cause: Fix B (mine, from the Retry Referral work) amplified an existing per-call push

Two separate things multiplied together.

**(a) The backfill decided "who needs an order id" one beneficiary at a time.** The step 0 I added
to `fetchBeneficiariesByStatus` ran, for every entry in `awaitingProviderResult +
awaitingManualEntry`:

```kotlin
val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId) ?: return@mapNotNull null
val existing = tbDao.getTbDiagnosticsByBenId(ben.beneficiaryId)
```

Two reads per entry, over the whole in-flight backlog, queued on the Room executor **before the
first fetch could start**. That is the `SELECT 1` starvation: Paging's refresh was sitting in the
same queue behind thousands of reads.

**(b) `fetchOrderResult` pushes the entire unsynced TB Suspected table, per call.** It already
ended with:

```kotlin
try { pushUnSyncedRecordsTBSuspected() } catch (e: Exception) { … }
```

That is correct for a single beneficiary — someone tapping *Fetch result* wants their row pushed.
Called N times from a sweep it becomes N table scans, N POSTs and N write-backs. Pre-existing
behaviour; my batch call site is what turned it into a flood. The same was true of the `completed`
bucket, which has been fanning out this way since before this work (measured at 4,000 entries for
XRAY_CHEST).

### H2.3 Fixes

**H2.3a — decide in bulk.** `TBDao.getBenIdsWith{Xray,TrueNat,Rif}OrderId(benIds)` asks SQLite which
beneficiaries already hold a usable order id, one statement per 900-id chunk, reading one column
instead of forty-six. `BenDao.getExistingBenIds(benIds)` does the "it may already be a local
beneficiaryId" fallback in bulk too. Two reads per beneficiary became two reads per 900.

**H2.3b — push once per sweep, not once per beneficiary.** `fetchOrderResult` gained
`pushSuspectedAfter: Boolean = true`. The default preserves today's behaviour for every
single-beneficiary caller; the two batch call sites (the backfill and the `completed` bucket) pass
`false` and push once after the whole batch.

**H2.3c — bound the backfill.** `orderIdBackfillPerSweep = 50`. The backlog drains across sweeps
instead of in one burst. Fix A (§ RCA\_RETRY\_REFERRAL) already stops the wrong *Retry Referral*
button, so a not-yet-filled id costs only a `Pending` label until a later sweep.

**Measured, same screen, after:**

| | before | after |
|---|---|---|
| `POST order/result` | 552 | 80 |
| `POST tb/suspected/saveAll` | 125 | 13 |

and the pushes now terminate cleanly — `TB Suspected push complete: 6 succeeded, 0 failed out of 6`
rather than re-running against the same rows.

### H2.4 Row decorations are not first-paint content

`AllBenFragment.onViewCreated` launched **seventeen** Room-Flow collectors — the per-row badges,
counts and status icons — before the first page was drawn. Several are whole-table reads:

| flow | rows |
|---|---|
| `childCounts` | 10,700 |
| `anthropometryFilledBenIds` | 10,697 |
| `diagnosisBenIds` | 10,163 |
| `allTbDiagnostics` | 10,163 × 46 columns |
| `vitalBenIds` / `tbScreeningBenIds` / `allTbScreening` | 9,987 each |

None of them decides whether the list appears; they only annotate rows that are already drawn.
They now start when Paging's first refresh settles (`startRowDecorationCollectors()`, triggered
from the `loadStateFlow` collector, with first-content-drawn as a backstop so an empty list still
gets them, and reset in `onDestroyView` because `onViewCreated` builds a fresh adapter).

Measured, flow emissions delivered *before* first content:

| | before | after |
|---|---|---|
| emissions before first content | 16–20 | **1** |

The deferral does exactly what it says. **It did not make the screen faster** — and that is the
useful part of the result, because it ruled the decorations out as the first-open bottleneck.

### H2.5 What the bottleneck actually is: SQLite read connections

`DbContentionSampler` was extended to dump every Room thread's top frames whenever the no-op probe
waits over a second. During a 4,337 ms stall, of 12 Room threads:

```
stoptb-room-query RUNNABLE      SQLiteConnection.nativeExecuteForCursorWindow  <- SQLiteCursor.getCount
stoptb-room-query RUNNABLE      SQLiteConnection.nativeExecuteForCursorWindow  <- SQLiteCursor.getCount
stoptb-room-query RUNNABLE      SQLiteConnection.nativeExecuteForCursorWindow  <- SQLiteCursor.getCount
stoptb-room-query TIMED_WAITING SQLiteConnectionPool.waitForConnection         (×18 across dumps)
stoptb-room-txn   WAITING       (parked in a coroutine await, holding nothing)
```

`journal_mode=wal` was confirmed at open, so writers are not blocking readers at the lock level.
The transaction threads are parked and holding nothing. What is saturated is the **SQLite
connection pool**: three readers are inside `getCount → nativeExecuteForCursorWindow`, filling
2 MB CursorWindows for whole-table results, and everything else — Paging's refresh included —
parks in `waitForConnection`.

That reframes the problem. It is not thread count (the query executor has 8 threads), not the
write lock, and not invalidation frequency on its own. It is **how many bytes the concurrent reads
have to move through a fixed, small number of connections**.

### H2.6 Cutting the bytes

**`allTbDiagnostics` narrowed from 46 columns to the 29 a card renders**
(`model/TbDiagnosticsCardFields.kt`, `TBDao.getAllTbDiagnosticsCardFields`). Wide rows matter here
beyond parse cost: the wider the row, the more times SQLite refills the CursorWindow, and each
refill holds a connection. The old `SELECT * FROM TB_DIAGNOSTICS` flow was **deleted** rather than
left unused, so the wide scan cannot be reintroduced by accident.

**A duplicate scan removed.** `diagnosisBenIds` combined `tbDiagnosticsBenIds`
(`SELECT benId FROM TB_DIAGNOSTICS`) with `tbSuspectedBenIds` — a second full scan of a table the
same screens were already reading in full. The four list screens now derive those ids from
`allTbDiagnostics` via `tbDiagnosticsBenIdsFromCards`, the pattern already used for TB_SCREENING's
three id flows. `ExamineViewModel` keeps the cheap id-only query: it is a single-beneficiary screen
and does not load the card list.

### H2.7 With the pull finished: what the screens actually cost

Once the camp hub had delivered the village and stopped writing (population stable at 13,600 for
five minutes), the same screens were re-measured. This is the number that matters for day-to-day
use, and it is a different world from the figures above:

**All Beneficiaries, 13,600 beneficiaries, 8 opens:**

```
8864   (first open after a cold app start)
1184  1184  1183  1071  1113  1008
```

Steady-state **~1.1 s**, and remarkably tight — six consecutive opens inside a 176 ms spread.
Every open presented.

**Digital Chest X-ray, ~960 members of 13,897 beneficiaries, 4 opens:**

```
6385  4033  3124  1730
```

The downward trend is the bounded backfill (§H2.3c) draining: each sweep fills 50 ids, so each
successive open has less to do. That is the fix behaving as designed.

So the multi-second figures in §H2.1 and §H2.4 are the cost of **opening a list while a camp hub
is bulk-ingesting a village**, not the cost of the list.

### H2.8 Deferring the status sweep too — tried, measured, reverted

If deferring the row decorations past first paint was right, deferring the
`getBeneficiariesByStatus` sweep looked right for the same reason: it is a background refresh of
data not yet on screen, and it writes TB_DIAGNOSTICS per beneficiary. It was implemented behind
the same gate and measured on the X-ray list:

| | opens (ms) | median |
|---|---|---|
| sweep in `onResume` (as shipped) | 6385, 4033, 3124, 1730 | ~3,579 |
| sweep deferred past first paint | 1437, 11852, 3402, 3613, 8755 | ~3,613 |

No median gain and markedly worse spread — deferring does not remove the work, it moves it into
the window where the user is navigating to the *next* screen, so it lands on that screen's
refresh instead. **Reverted.** Recorded here for the same reason as §E3: the obvious next idea
has already been tried.

### H2.9 What is left, and why I stopped here

With the camp pull running, "first content" on **All Beneficiaries** stayed in the 1.2–11.8 s range
across every build, with 4–5 of 8–9 opens not presenting inside a 12 s window. I cannot claim an
improvement there from those measurements, and I am not going to: the dataset grew 27% between the
runs and the writer got busier with it. §H2.7 is the honest number for the same screen once the
ingest finished — ~1.1 s — which places the whole 1.2–11.8 s range on the ingest, not the list.

What the thread dump does establish is where the remaining time goes — reads queued on a saturated
connection pool while the pull ingests ~14k beneficiaries 50 rows at a time (`[pull] page 279:
server sent 50 beneficiaries -> 50 will be written`).

And on the **X-ray list specifically it is not slowness at all, it is a livelock.** Restarting the
app (which re-runs the whole camp pull) and sitting on that screen for four and a half minutes:

```
NEW PagingSource (query='' source=6)   × 4
[paging] load …                        × 0
```

Four refreshes started, **none completed** — each was invalidated by the next pull write before
its query returned, so the spinner never cleared. This is the same signature recorded against the
executor split earlier in this report ("383 PagingSource creations and zero load() calls: the list
was not slow, its query was never executed"), reappearing for a different reason: not thread
starvation now, but an invalidation rate the refresh cannot outrun. Sources 5–10 query TB tables
by necessity, so Room invalidates them on every TB write; there is no read-side escape the way
there was for sources 0–4 (`searchBenPagedWithoutTbTables`).

The levers left are on the pull side:

- fewer, larger write transactions during a bulk ingest (`pullWriteChunkSize = 500` exists but the
  page size is 50, so it never engages);
- or backing the pull off while a list screen is in the foreground.

Both are changes to pull-sync internals owned by another developer, so they are written up here as
a recommendation rather than made. They need a decision, not a patch.

---

## I. Remaining issues
1. **The remaining ~10 call sites still fetch a whole `BenRegCache` to read one id.**
   `MIGRATION_59_60` turned each of those scans into an index lookup, which is where the win came
   from, but they still materialise a wide beneficiary row per record. The per-chunk bulk pattern
   written for TB screening (`getBenIdsByRegIds` + `getTbScreeningByBenIds` + batched insert) is a
   template for the other domains if more is wanted. Lower priority now that the scans are gone.

2. ~~**`allTbDiagnostics` still loads the whole table as wide entities.**~~ **Done — §H2.6.**
   Narrowed to the 29 columns a card renders (`TbDiagnosticsCardFields`), the wide `SELECT *` flow
   deleted, and the duplicate `SELECT benId FROM TB_DIAGNOSTICS` scan removed from the four list
   screens. It still loads every row in the table, which remains the structural issue: the
   decorations want ~10 visible rows and read ~10k. Bounding that to the selected village needs a
   join against BENEFICIARY, which would add BENEFICIARY to the flow's invalidation set — a bad
   trade while a beneficiary pull is running. Left as is deliberately.
3. **~1,138 TB Suspected rows stuck UNSYNCED** — "reconciliation SKIPPED, will remain unsynced
   until a push of this local row succeeds". Pre-existing, unrelated to this work.
4. **`vendor/health` takes ~5 s per call**, twice per X-ray screen open. Backend/hub latency, not
   app code, but it dominates that screen's API time.
5. **The same U+202F bug exists** in `KalaAzarRepo`, `LeprosyRepo`, `MalariaRepo`, `AESRepo`,
   `FilariaRepo`, `NewHouseholdViewModel`, `HelperUtil` — each a one-line switch to
   `ServerDateParser`. Left alone because no vector-borne data was in play.
6. **Sources 5–10 still use the TB-observing query** — correct for them (they genuinely depend on
   TB tables), but they will still churn while the sync runs. Measured on a camp device this is
   now the **worst remaining symptom**: during a full camp re-pull the X-ray list livelocks —
   four Paging refreshes started, zero completed, spinner never cleared in 4.5 minutes (§H2.9).
   The fix is to lower the invalidation rate, which is pull-side and needs sign-off.
7. **Temporary instrumentation must be removed before release:** `utils/ScreenLoadTrace.kt`,
   `utils/TimingPagingSource.kt`, `utils/DbContentionSampler.kt` (including its Room-thread dump,
   §H2.5), `network/interceptors/PerfTimingInterceptor.kt`, `BenDao.perfProbe`, the
   `journal_mode` probe in `InAppDb`'s `RoomDatabase.Callback`, and the hooks in
   `IconGridAdapter`, `BenListAdapter`, `AllBenFragment`, `RecordsRepo`, `BenRepo`.
   `startRowDecorationCollectors()` in `AllBenFragment` is **not** instrumentation — it is the
   §H2.4 fix and stays, though the `ScreenLoadTrace.flowEmission` calls inside it go.
8. **Not tested:** release/SQLCipher build, 50k/100k/1M datasets, offline registration round trip,
   p90/p95 at adequate sample size.

---

## J. Reproduction

```bash
# build + install
sh gradlew :app:assembleStoptbUatDebug
adb -s <serial> install -r app/build/outputs/apk/stoptbUat/debug/app-stoptbUat-debug.apk

# capture
adb -s <serial> logcat -c
adb -s <serial> logcat --pid=$(adb -s <serial> shell pidof org.piramalswasthya.stoptb.uat) -v time > run.log

# what to read
grep 'FIRST CONTENT DRAWN' run.log        # time to first content
grep 'REFRESH(count+page0)' run.log       # paging load duration and row count
grep -c 'NEW PagingSource' run.log        # invalidation churn (1 per screen open is correct)
grep '\[db\] executor made' run.log       # executor queueing — this is the open bottleneck
grep 'TB_DATE_PARSE' run.log              # must be zero

# query cost on-device, against the synthetic replica (never the live DB)
adb -s <serial> shell "cd /data/local/tmp && ./sqlite3 perf.db < bench.sql"
```

Unit tests: `sh gradlew :app:testStoptbUatDebugUnitTest`
