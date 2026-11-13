---
name: android-architecture-review
description: Review STOP-TB code for architecture and concurrency problems — repository boundaries, ViewModels, Flow collectors, coroutine scopes, duplicate requests, stale state, race conditions, Room entity updates and API orchestration. Read-only review with file:line findings.
---

# Android Architecture Review (STOP-TB)

Read-only. Trace before judging; report findings, don't fix.

## Scope
Ask for the target: a feature (e.g. `ui/contact_tracing`), a diff (`git diff main...HEAD`), or a ticket. Use subagents only for independent investigations, never for parallel edits to the same files.

## Trace the full path
UI event (click / text change / fragment lifecycle) → ViewModel → repository → DAO / Retrofit API / WorkManager → state back to UI (LiveData / StateFlow / binding).

## Checklist
**Lifecycle and state**
- Flows collected in `lifecycleScope.launch` without `repeatOnLifecycle` / `flowWithLifecycle` → duplicate collectors after back navigation
- `viewLifecycleOwner` vs `this` for LiveData observers in Fragments
- State kept in the Fragment instead of the ViewModel / `SavedStateHandle`; lost on rotation or process death
- One-time events (navigation, toast) sent as sticky LiveData → replayed on revisit

**Concurrency**
- Double-tap submit → duplicate inserts or API calls (no in-flight guard)
- Read-modify-write on Room entities without a transaction → lost updates
- UI saves racing WorkManager push/pull workers on the same rows (sync state flags, temp → final IDs)
- `GlobalScope`, `runBlocking` on the main thread, blocking calls in `Dispatchers.Main`
- Uncancelled jobs; exceptions swallowed in `catch (e: Exception) {}`

**Data layer**
- Repository leaking DTOs into UI, or entity ↔ DTO mapping done in Fragments
- Same API/DB call triggered from several places
- Stale caches: ViewModel holding a snapshot instead of observing the DB
- Hilt scopes: `@Singleton` holding a Context/View, or wrong scope for repositories

**Shared code**
- CFU shares code with Community/Occupation contact tracing: flag any change that alters non-CFU behaviour; CFU-specific logic must stay in guarded CFU-only functions.

## Output
Table ranked by severity: `severity | file:line | issue | concrete failure scenario | suggested minimal fix`. Separate confirmed issues (traced end-to-end) from suspicions.
