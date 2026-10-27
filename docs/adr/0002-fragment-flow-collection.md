# ADR-0002: Collect flows on the view lifecycle (`launchOnView`)

- Status: Accepted
- Date: 2026-10-28

## Context

Beneficiary and TrueNat lists got progressively slower during a session even with no new data.
`AllBenFragment` started ~26 collectors with `Fragment.lifecycleScope` in `onViewCreated`. That
scope outlives the view, so each return from a form added another full set of collectors; after
N round trips every DB invalidation was processed N×26 times on the main thread, also while the
screen was in the back stack.

## Decision

Fragments collect flows with `Fragment.launchOnView { }` (`utils/FragmentLifecycleExt.kt`), which
runs on `viewLifecycleOwner.lifecycleScope` inside `repeatOnLifecycle(STARTED)`. Form screens whose
collectors have side effects may use `viewLifecycleOwner.lifecycleScope.launch`. Adapters/dialogs
built in `onViewCreated` are released in `onDestroyView`.

## Consequences

- Collectors stop with the view and pause in the background — no accumulation.
- Collectors restart on return to the screen; they must be idempotent (Paging data is
  `cachedIn(viewModelScope)` so lists do not reload).

## Alternatives considered

- Move the subscriptions into the ViewModel with `stateIn` only — still needs a view-scoped
  collector in the fragment; complementary, not a replacement.
- `launchWhenStarted` — deprecated; suspends instead of cancelling upstream work.
