---
name: diagnostic-worker
description: >-
  Adds or changes STOP-TB background work and camp-hub diagnostics: Hilt workers, WorkerUtils
  enqueue helpers, unique-work policies, the X-ray/TrueNat/RIF order push and result polling
  (DiagnosticOrderPushWorker, DiagnosticResultPollWorker, TBRepo.fetchBeneficiariesByStatus),
  and screen-triggered refreshes — without duplicate runs or DB invalidation storms. Use when
  touching anything in work/, sync/push/pull, order status refresh, polling intervals, or when
  sync makes screens lag.
knowledge:
  - knowledge/stack.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Diagnostic & Background Workers

## Knowledge

[stack.md](../../../knowledge/stack.md) (sync + diagnostics), [android-conventions.md](../../../knowledge/android-conventions.md)
(Workers & network refresh). Code: `work/WorkerUtils.kt`, `work/DiagnosticResultPollWorker.kt`,
`work/DiagnosticOrderPushWorker.kt`, `work/BasePushWorker.kt`, `repositories/TBRepo.kt`.

## Process

1. **New worker**: `@HiltWorker class XWorker @AssistedInject constructor(@Assisted ctx, @Assisted params, deps…)`;
   push workers extend `BasePushWorker` and implement `doSyncWork()`; add a `companion object { const val name = "..." }`
   and an enqueue helper in `WorkerUtils` — never enqueue ad hoc from UI.
2. **Policy**:
   - triggered from UI/resume → `enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, req)`;
   - a worker rescheduling itself (poll loop) → `REPLACE` with `setInitialDelay`;
   - per-beneficiary chains use the per-ben unique name (`DIAGNOSTIC-ORDER-PUSH-<benId>`).
3. **One trigger per screen**: a refresh (e.g. `fetchBeneficiaryStatuses`) runs from exactly one
   place (ViewModel `init` *or* `onResume`), throttled (skip if last run < 30–60 s). Do not also
   start the poll worker for the same data on every `onViewCreated`.
4. **Writes inside workers/sweeps**: per [room-dao-query](../room-dao-query/SKILL.md) — load lookups
   once, group a bucket's writes in `db.withTransaction`, skip unchanged rows; network calls stay
   outside the transaction. Keep `withBenIdLock(benId)` around read-check-write of a
   beneficiary's `TB_DIAGNOSTICS` row.
5. **Gating**: diagnostics only when `preferenceDao.isCampModeEnabled() && isCampHubConnected()`
   and the device is integrated (`isXrayIntegrated` / `isTruenatIntegrated`).
6. **Concurrency**: bounded parallel network (`Semaphore`, see `MAX_CONCURRENT_POLLS`).
7. **Logging**: summary counts only — never whole ID lists or bodies (SyncLogTree persists them).
8. **Test**: repository decision logic as unit tests; status-bucket writes as a DAO/repo test
   ([android-test](../android-test/SKILL.md)); manual: hub connected/disconnected, app backgrounded.

## Rules

| Rule | Detail |
|------|--------|
| KEEP from UI | REPLACE only for self-reschedule |
| Single trigger | One throttled refresh per screen |
| Batched writes | Transaction per bucket, skip no-ops |
