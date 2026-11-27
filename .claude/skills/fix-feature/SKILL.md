---
name: fix-feature
description: Diagnose a STOP-TB bug that turns up inside feature work, and fix it minimally — wrong form logic, sync failures, migration errors, camp-mode issues — with a list of known hot spots. For a Stop-XXX ticket, bug-fix-workflow is the main process; this skill adds the hot-spot checklist.
---

# Fix Feature

Follows the same team rules as `bug-fix-workflow` (approval before edits, minimal fix, Edit tool only, branch and commit conventions). Use this skill's hot-spot list while finding the root cause.

## Process

1. **Reproduce on paper.** Identify branch, variant, role, screen and steps. Unclear → ask (use `grill-me` for more than one question).
2. **Locate.** Trace from the screen (`ui/…Fragment` → `ViewModel` → `Repository` → `Dao`/`AmritApiService`/`Worker`). For sync bugs read the `WorkerUtils` push chain and the repo's `pushUnSynced*` method; for crashes on upgrade check the migrations in `InAppDb` (see `room-migration` Step 0 for the branch's style).
3. **Root cause, not symptom.** State it in one or two sentences with file:line.
4. **Propose and wait for OK**: files to change, one line per change, any migration.
5. **Fix** with the smallest change that removes the cause. Schema fix → `room-migration` (never edit an already-shipped migration; add a new one).
6. **Tests**: not for bug fixes unless the user asks — then use `add-unit-test` (failing test first).
7. **Finish** with `review-work` → `verify-feature`, plus numbered manual test steps.

## STOP-TB bug hot spots

- Temp `benId` replaced by the server ID after push — re-resolve before loading and saving (Stop-727 fixes on release-2.3).
- Rows stuck in `SYNCING` after a crash (`recoverOrphanedSyncStates`), or failed pushes marked `SYNCED`.
- Missing string in `values-hi`/`values-as` (falls back to English, or crashes on format args).
- Room `IllegalStateException: Migration didn't properly handle` → migration SQL doesn't match the entity exactly (column types, NOT NULL, defaults, indices).
- Camp mode: hub URL/health check before camp APIs — `CampHubUrlValidator` / `CampHubReachability` exist on release-2.3 and 2.4 only.
- CFU shares code with Community/Occupation contact tracing — a fix in the shared path changes all three.
