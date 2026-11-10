---
name: add-sync-flow
description: Add or change how a STOP-TB record syncs with the AMRIT server — Retrofit endpoint, request/response models, repository push/pull with syncState, Hilt worker and its place in the WorkerUtils push chain. Use for new synced entities, new server fields, or push/pull bugs.
---

# Add Sync Flow

Reference: TB screening → `repositories/TBRepo.kt` (`pushUnSyncedRecordsTBScreening`) + `work/PushTBToAmritWorker.kt` + `work/PullTBFromAmritWorker.kt`.

## Step 0 — Detect the branch's sync style (never assume)

```bash
grep -nE "fun (enqueuePushChain|triggerAmritPushWorker|triggerAmritPullWorker|isCampHubSyncActive)" app/src/main/java/org/piramalswasthya/stoptb/work/WorkerUtils.kt
ls app/src/main/java/org/piramalswasthya/stoptb/work/SyncGateWorker.kt app/src/main/java/org/piramalswasthya/stoptb/utils/sync/BatchIsolation.kt 2>/dev/null
grep -n "PushMutex" app/src/main/java/org/piramalswasthya/stoptb/repositories/TBRepo.kt
```

| | release-2.3, 2.4 | release-2.5 | release-1.1 … 2.2.3, main |
|---|---|---|---|
| Push entry | `triggerAmritPushWorker` → debounced `SyncGateWorker` → `enqueuePushChain` | `triggerAmritPushWorker` builds the chain directly | same as 2.5 |
| Chain | registration (+ vitals) → TB / ABHA / counselling / contact tracing in parallel | registration first, dependents after | registration first, dependents after |
| Batch isolation | `utils/sync/BatchIsolation.kt` (+ `BatchIsolationTest`) | per-chunk loop in the repo | per-chunk loop in the repo |
| Push mutex in repo | ✓ (`tbScreeningPushMutex`) | ✓ | ✗ — follow the nearest repo's pattern |
| Camp mode | `isCampHubSyncActive`, `CampHubReachability`, camp request builders | `isCampHubSyncActive` | `isCampHubSyncActive` (not on 1.1) |
| Heartbeat | `SyncHeartbeatWorker` | — | — |

Add the new worker the way the branch already wires the others; don't port the 2.3 design onto an older branch as part of a feature.

## Pieces

| Piece | Where |
|---|---|
| Endpoint | `network/AmritApiService.kt` (`@POST`/`@GET`, suspend, `Response<…>`) |
| Payload / response models | `network/` or `model/` (Moshi; mirror server field names exactly) |
| Local read of pending rows | DAO query `WHERE syncState = UNSYNCED` |
| Push / pull logic | `repositories/<Domain>Repo.kt` |
| Worker | `work/` — push workers extend `BasePushWorker`, are `@HiltWorker` with `@AssistedInject` |
| Scheduling | `work/WorkerUtils.kt` — see Step 0 |

## Push

1. Read `UNSYNCED` rows; mark `SYNCING`; send in chunks so one bad chunk doesn't block the rest.
2. On success mark `SYNCED` and store server IDs; on failure **set back to `UNSYNCED`** so the next cycle retries. Never drop rows.
3. Guard against concurrent pushes with the repo's `Mutex` pattern where the branch has it.
4. Handle token expiry the same way existing calls do (refresh then retry); log with Timber, no PII.
5. Add the worker to the correct branch of the push chain (registration runs first; TB/ABHA/counselling/contact tracing after). Respect camp mode (`isCampHubSyncActive`).

## Pull

1. Page through the server, upsert inside a transaction per page, don't overwrite local `UNSYNCED` edits.
2. Update the last-pull timestamp only after a successful page set.
3. Chain into `triggerAmritPullWorker` before `UpdatePrefForPullCompleteWorker`.

## Tests (new feature: always, via `add-unit-test`)

- JVM: payload mapping (like `GeneralOpdSaveRequestTest`, 2.3/2.4/2.5), batch isolation (like `BatchIsolationTest`, 2.3/2.4), repo logic with mocked `AmritApiService`.
- Schema change for new local fields → `room-migration`.

## Rules

- Offline-first: the UI saves locally and returns; syncing is always background.
- Don't change `BasePushWorker` / chain order without the user agreeing — it affects every module.
- Keep CFU sync logic separate from Community/Occupation contact tracing code paths.
- Show the touch list and wait for OK before editing several files; edit with the Edit tool.
