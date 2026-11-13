---
name: offline-sync-debug
description: Debug STOP-TB offline sync — WorkManager push/pull chains, retries, SyncState transitions, temp→final beneficiary ID swaps, duplicate/lost/stuck records, and conflicts between UI saves and background sync.
---

# Offline Sync Debugging (STOP-TB)

## Project facts
- Workers: `app/src/main/java/org/piramalswasthya/stoptb/work/` (PushToAmritWorker, PullFromAmritWorker, PushTBToAmritWorker, GenerateBenIdsWorker, ContactTracingSyncWorker, CounsellingSyncWorker, `dynamicWoker/`, ...). Base class: `BasePushWorker`.
- Scheduling: `work/WorkerUtils.kt` — `triggerAmritPushWorker`, `triggerAmritPullWorker` (unique work, `KEEP` / `REPLACE` when forced), `triggerCampAwarePushWorker`, camp-hub handling.
- Row state: `SyncState { UNSYNCED, SYNCING, SYNCED }` in `database/room/DbUtils.kt`; `SakhiApplication.recoverOrphanedSyncStates()` resets rows stuck in SYNCING.
- Beneficiary IDs: `BeneficiaryIdsAvailDao` + `GenerateBenIdsWorker`; temp IDs are swapped for final server IDs (see the Stop-727 fixes in git history).

## Steps

1. **Pin down the symptom**: record never syncs / syncs twice / stuck SYNCING / wrong benId on form / data overwritten by pull / sync never finishes. Get the entity, flavor and steps.

2. **Inspect WorkManager state on the device:**
   ```bash
   adb shell dumpsys jobscheduler | grep -A5 org.piramalswasthya.stoptb
   adb logcat -v time | grep -iE "WM-|Worker|Amrit|sync"
   ```
   The app also has `SyncLogTree` logs — check the in-app sync log where available.

3. **Inspect row state** in a pulled debug DB (see `/room-sqlite-optimization` for the pull command):
   ```sql
   SELECT syncState, count(*) FROM <table> GROUP BY syncState;
   SELECT * FROM <table> WHERE benId = <id>;
   ```
   Check whether the temp ID is still referenced in child tables (forms, vitals, TB, contact tracing) after the swap.

4. **Trace the worker code**: query that selects unsynced rows → DTO mapping → API call → response handling → state update. Look for:
   - state set to SYNCED before the API confirms, or not reset on failure / `Result.retry()`
   - rows edited by the UI while SYNCING (edit lost, or the stale copy pushed)
   - pull overwriting unsynced local edits
   - chain order (registration before dependent forms) and `KEEP` swallowing a needed re-run
   - temp→final ID swap not applied to every referencing table, or applied twice
   - non-idempotent pushes creating server duplicates on retry

5. **Report** the root cause with file:line and a reproducible sequence (e.g. "save form while push in flight → ..."), then propose a minimal fix and wait for approval.

## Rules
- Use test accounts and synthetic data only; never paste real beneficiary data or tokens into chat.
- Minimal fixes; keep CFU logic separate from Community/Occupation code paths.
- Verify by compile and a manual sync run, both online and with airplane mode toggled.
