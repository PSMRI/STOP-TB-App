# RCA — "Retry Referral" shown by default on X-Ray and TrueNat cards

**Reported:** the *Retry Referral* button appears on the X-Ray and TrueNat cards as soon as a
beneficiary record appears, with no referral failure and no retry attempted.

**Status:** root cause identified, fixed, verified on device.

**Severity:** functional, with a data risk. The button's action is `RETRY_PUSH`, which raises a
*new* order against the backend. Offering it for a referral that already succeeded invites
duplicate diagnostic orders for the same beneficiary and test.

---

## 1. What the user sees

A beneficiary referred from the **registrar** tab reaches the **nurse** tab showing:

```
MTB Order ID : Pending
[ Retry Referral ]            ← wrong: the order already exists on the backend
```

Expected:

```
MTB Order ID : 759515440116-130721-MTB
[ Enter Result ]  [ Not Conducted ]
```

---

## 2. Root cause

Two independent facts combine. Neither is a bug on its own.

### 2a. The status sweep never learns the order id

`getBeneficiariesByStatus` is the only call that tells the app which orders exist. Its response
carries **five buckets of `benRegId` and nothing else** — no order id anywhere
(`network/JsonAdapters.kt`, `DiagnosticBeneficiaryStatusData`):

```kotlin
data class DiagnosticBeneficiaryStatusData(
    val awaitingProviderResult: List<Long>? = emptyList(),
    val completed: List<Long>? = emptyList(),
    val failed: List<Long>? = emptyList(),
    val closed: List<Long>? = emptyList(),
    val awaitingManualEntry: List<Long>? = emptyList()
)
```

`TBRepo.fetchBeneficiariesByStatus` writes a **status** for each bucket and leaves the id null:

```kotlin
it.copy(
    trueNatOrderStatus = OrderStatus.PENDING.name,   // from awaitingProviderResult
    isReferredForSputum = true,
    syncState = SyncState.SYNCED
)                                                     // trueNatOrderId stays null
```

Only the `completed` bucket called `fetchOrderResult` — the one API that returns
`externalOrderId`. `awaitingProviderResult` and `awaitingManualEntry` never did, so a beneficiary
whose test is genuinely in flight keeps a null order id indefinitely.

### 2b. The card treats "no order id" as "the referral failed"

`BenListAdapter` picks the button with this chain (identical shape for X-Ray, TrueNat and RIF):

| # | condition | button |
|---|---|---|
| 1 | `referred == false` | TEST REFUSED |
| 2 | `status == FAILED` | **Retry Referral** ← the legitimate one |
| 3 | `status == COMPLETED` | View result |
| 4 | `status == CLOSED` | Create new order |
| 5 | **`orderId.isMissingOrderId()`** | **Retry Referral** ← the defect |
| 6 | else | Enter Result + Not Conducted |

A record from 2a arrives with `status = PENDING` or `MANUAL_ENTRY` and `orderId = null`. It misses
rows 1–4 and lands on row 5, so every such beneficiary renders *Retry Referral*.

### 2c. Why it stayed hidden

The inference in row 5 — "no order id means nothing was ever created" — is only true for a
referral this device failed to push. It silently stopped being true once the status sweep began
writing statuses without ids. Nothing failed, nothing logged, and the button looks deliberate.

---

## 3. Fix

### Fix A — do not offer retry when the backend has already reported an order

`adapters/BenListAdapter.kt`, three sites: X-Ray (`:794`), TrueNat (`:844`), RIF (`:376`).

```kotlin
// before
tbDiag?.trueNatOrderId.isMissingOrderId() -> …Retry Referral…

// after
status.isNullOrBlank() && tbDiag?.trueNatOrderId.isMissingOrderId() -> …Retry Referral…
```

Every status the app stores for these tests comes from `getBeneficiariesByStatus`, and all five of
its buckets mean *an order exists*. A genuine failure is already caught at row 2, so the only case
left for retry is the one with no backend evidence at all: **no status and no id** — a referral
this device never managed to push. This is also the branch that must hold offline, where no fetch
can repair the missing id.

### Fix B — backfill the missing order id

`repositories/TBRepo.kt`, new step before bucket 1 (`:3151`). For `awaitingProviderResult` +
`awaitingManualEntry`, when the local id is absent, call `fetchOrderResult` to fetch and store it.

Three constraints shaped it:

- **Must run outside `withBenIdLock`.** `fetchOrderResult` takes the same per-benId lock itself
  and `kotlinx.coroutines.Mutex` is not reentrant, so calling it inside would deadlock.
- **Must be bounded.** This list is the whole in-flight backlog. It is chunked at
  `completedFetchChunkSize`, the same bound the `completed` bucket uses after one coroutine per
  entry caused a 2,664-job cancellation stall.
- **Must be self-limiting.** It only runs when the id is genuinely absent, so it costs nothing on
  every later sweep once stored.

Fix A is the one that resolves the reported symptom; B makes the real order id visible on the card
and is what stops the condition recurring.

### Fix B had to be rewritten after camp testing

As first written, B was correct but far too expensive, and testing against a local camp hub with
~10k beneficiaries showed it clearly. Two problems, neither visible on a small dataset:

- **It decided who needed an id one beneficiary at a time** — a `getBenByRegId` plus a whole-row
  `getTbDiagnosticsByBenId` for every entry in the in-flight backlog, all queued on the Room
  executor before the first fetch could start.
- **`fetchOrderResult` already ends with `pushUnSyncedRecordsTBSuspected()`** — right for one
  beneficiary, ruinous in a batch. Calling it N times meant N scans of the unsynced table, N
  POSTs and N write-backs.

Together, one open of the X-ray list produced **552 `order/result` POSTs and 125
`tb/suspected/saveAll` POSTs**, and the list it was triggered from never settled — its Paging
source was re-created five times in 28 seconds while a no-op `SELECT 1` waited 3.5 s for the
database.

The rewrite keeps the same behaviour and makes it cheap: the "who is missing an id" question is
answered in bulk (`getBenIdsWith{Xray,TrueNat,Rif}OrderId`, one statement per 900-id chunk, one
column instead of forty-six); `fetchOrderResult` gained `pushSuspectedAfter: Boolean = true` so
batch callers push once after the batch rather than once per beneficiary; and the backfill is
capped at `orderIdBackfillPerSweep = 50`, draining across sweeps. Same screen after: **80**
`order/result` and **13** `saveAll`. Full write-up in
`docs/performance/PERFORMANCE_RCA_AND_OPTIMIZATION_REPORT.md` §H2.

Capping the backfill is safe precisely because Fix A is the fix for the reported bug: an id that
has not been filled yet costs a `Pending` label, not a wrong button.

---

## 4. Verification (device: motorola edge 20, Android, UAT build)

Same beneficiary, SONA SINGH `759515440116`, before and after:

| | before | after |
|---|---|---|
| MTB Order ID | `Pending` | `759515440116-130721-MTB` |
| Button | **Retry Referral** | **Enter Result** + **Not Conducted** |

Digital Chest X-ray behaves the same — real ids (`527610712448-924588-XRAY_CHEST`) and the correct
actions. Build clean; 65 unit tests, 0 failures.

---

## 5. Risk and scope

- Additive and conservative: it can only *remove* a Retry Referral that should not have been
  offered. A referral that genuinely failed still shows it, via the untouched `FAILED` branch, and
  so does one that was never pushed (no status, no id).
- RIF was fixed alongside X-Ray and TrueNat. It carried the same chain and the same defect, and
  was not in the original report.
- No schema change, no API change.

## 6. Follow-up worth considering

The underlying awkwardness is that `getBeneficiariesByStatus` reports *which* orders exist but not
*what* they are, forcing an N+1 `fetchOrderResult` to recover ids the server already knows. If the
backend added `orderId` to each bucket entry, Fix B could be deleted and the sweep would be one
round trip instead of one per beneficiary. Worth raising with the backend team.
