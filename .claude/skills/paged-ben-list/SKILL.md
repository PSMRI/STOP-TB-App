---
name: paged-ben-list
description: >-
  Builds or fixes STOP-TB beneficiary-style list screens (All Beneficiaries, TrueNat/X-ray referral
  lists, TB lists) with Paging 3, BenPagingAdapter/BenListAdapter, search with debounce, and
  per-row status overlays — keeping scroll and search smooth at 5k+ records. Use when adding a
  list screen, adding a badge/status to list cards, or fixing a laggy, slow-loading or freezing list.
knowledge:
  - knowledge/stack.md
  - knowledge/android-conventions.md
  - knowledge/testing.md
---

# Paged Beneficiary List

## Knowledge

[android-conventions.md](../../../knowledge/android-conventions.md) (Lists & adapters, Fragments).
Reference implementation: `ui/home_activity/all_ben/AllBenViewModel.kt` + `AllBenFragment.kt` +
`adapters/BenPagingAdapter.kt`.

## Process

1. **Query**: `PagingSource` from the DAO ([room-dao-query](../room-dao-query/SKILL.md)).
2. **ViewModel**: `combine(filter, kind…) → debounce(300 on text) → flatMapLatest { Pager(PagingConfig(pageSize = 30, prefetchDistance = 10)) { … }.flow }
   .map { it.map(::toDomain) }.cachedIn(viewModelScope)`.
3. **Per-row overlays** (vitals done, screening done, order status…): one ViewModel flow per
   concern, village-scoped, mapped to `Set<Long>` / `Map<Long, T>` with
   `.distinctUntilChanged().flowOn(Dispatchers.Default)`. Prefer **one combined state object**
   over many separate flows.
4. **Adapter**:
   - store overlays as `HashSet`/`HashMap`; `bind()` does O(1) lookups only;
   - on overlay update, compute changed IDs and `notifyItemChanged(i)` only for those rows —
     never `notifyDataSetChanged()`;
   - no `Regex`, date formatters, `ContextCompat.getDrawable().mutate()` per bind — hoist/cache;
   - `bind()` never triggers work (no WorkManager/DB calls from bind; if needed, do it in the
     ViewModel for the whole page once).
5. **Fragment** ([fragment-lifecycle](../fragment-lifecycle/SKILL.md)): every collector via
   `launchOnView`; `submitData` in `collectLatest`; `rv.setHasFixedSize(true)`,
   `supportsChangeAnimations = false`; clear adapter in `onDestroyView`.
6. **Search**: attach the `TextWatcher` once in `onViewCreated`; trimmed text → `viewModel.filterText`.
7. **Verify** with the testing.md smoke checklist on ≥ 5k beneficiaries (open, scroll, search,
   10× form round-trip, 3 min idle with polling).

## Rules

| Rule | Detail |
|------|--------|
| Paged | No `Flow<List>` of the full village for lists |
| O(1) bind | Sets/Maps only |
| Targeted refresh | No full rebind on status changes |
