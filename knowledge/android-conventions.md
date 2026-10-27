# Android conventions — musts for this codebase

Short, enforceable rules. `review-work` checks changed files against this list. Why each rule exists:
see `docs/adr/`.

## Fragments & lifecycle

- Collect flows in fragments with `launchOnView { }` (`utils/FragmentLifecycleExt.kt`). Never
  `lifecycleScope.launch { flow.collect { } }` in `onViewCreated` — Fragment.lifecycleScope outlives
  the view and collectors pile up on every back-navigation (ADR-0002).
- Form screens whose collectors have side effects (trigger workers, re-submit form lists) may use
  `viewLifecycleOwner.lifecycleScope.launch` instead (cancelled with the view, not paused).
- Never start a collector inside a click listener, `TextWatcher`, or LiveData observer.
- Anything created in `onViewCreated` (adapters, dialogs) is released in `onDestroyView`:
  `binding.rv.adapter = null`, dismiss dialogs, `_binding = null`.
- One-off suspend work from a click: `viewLifecycleOwner.lifecycleScope.launch`.

## Lists & adapters

- Lists that can exceed a few hundred rows use Paging (`PagingDataAdapter` + Room `PagingSource`,
  `cachedIn(viewModelScope)`).
- Per-row lookup data passed to adapters is a `Set<Long>` / `Map<Long, T>` — never `List.contains`
  or `list.find` inside `bind()`.
- No `notifyDataSetChanged()` for status updates; diff and `notifyItemChanged` only changed rows.
- No allocations of `Regex`, `SimpleDateFormat`, drawables-with-tint per bind — hoist to a
  `companion object` or cache.
- Mapping/filtering lists of entities happens off the main thread
  (`.flowOn(Dispatchers.Default)` in the ViewModel), not in the fragment.

## Room / DAO

- Every query feeding the UI is scoped to the selected village (or an explicit village list).
- No `SELECT *` full-table `Flow` for UI badges — select only the columns needed.
- Writes of more than one row go through `db.withTransaction { }` (one invalidation per batch).
- Skip writes when the new row equals the stored row; prefer `@Update`/`@Upsert` over
  `OnConflictStrategy.REPLACE` for rows observed by the UI (REPLACE = delete + insert).
- Columns used in `JOIN`, `WHERE` or correlated subqueries are indexed.
- Do not add correlated subqueries per row to `ORDER BY` on large lists; precompute a sort key.
- Schema change ⇒ version bump + `Migration` + added to the builder + migration test. Never rely on
  `fallbackToDestructiveMigration()` (it deletes unsynced data).

## Workers & network refresh

- UI-triggered unique work uses `ExistingWorkPolicy.KEEP`; `REPLACE` only for a worker
  rescheduling itself.
- A network refresh runs from one place per screen (not `init` **and** `onResume`), with a
  minimum interval (throttle) — not on every resume.
- Repositories do network + DB on `Dispatchers.IO`.
- Per-beneficiary diagnostic writes go through `withBenIdLock(benId)`.

## Logging

- Never log whole lists/maps or response bodies at `d`/`e` level — `SyncLogTree` forwards
  sync-related messages to an in-memory buffer and a log file.
