---
name: fragment-lifecycle
description: >-
  Applies STOP-TB fragment lifecycle rules: collect flows with launchOnView (view-scoped,
  paused when stopped), avoid collectors in listeners/observers, release adapters, dialogs and
  binding in onDestroyView. Use when writing or reviewing a Fragment/BottomSheet, when a screen
  gets slower after navigating back and forth, duplicates events, or leaks.
knowledge:
  - knowledge/android-conventions.md
---

# Fragment Lifecycle

## Knowledge

[android-conventions.md](../../../knowledge/android-conventions.md) (Fragments), ADR
[0002](../../../docs/adr/0002-fragment-flow-collection.md). Helper: `utils/FragmentLifecycleExt.kt`.

## Process

1. **Collectors** in `onViewCreated`:

```kotlin
launchOnView { viewModel.someFlow.collectLatest { render(it) } }
```

   Form screens whose collector has side effects (enqueue worker, re-submit form list):
   `viewLifecycleOwner.lifecycleScope.launch { ... }` instead.
2. **Never** `lifecycleScope.launch { flow.collect }` (fragment scope) and never
   `launchWhenStarted`. Never launch a collector inside a click listener, `TextWatcher`,
   or `LiveData.observe` block — collect once and branch on state.
3. **LiveData**: `observe(viewLifecycleOwner)`, never `observe(this)`.
4. **One-off actions** from clicks: `viewLifecycleOwner.lifecycleScope.launch { }`.
5. **Cleanup**:

```kotlin
override fun onDestroyView() {
    dialog?.dismiss(); dialog = null
    _binding?.rvAny?.adapter = null
    _binding = null
    super.onDestroyView()
}
```

6. **Events** (toasts, navigation): from a ViewModel `StateFlow` reset after handling, or a
   `Channel`/`SharedFlow(replay = 0)` — so restarting collectors does not replay them.
7. **Check**: navigate into a child form and back 10×, then change one record — each collector
   should fire once (temporary `Timber.d` counter).

## Rules

| Rule | Detail |
|------|--------|
| View scope | Collectors die with the view |
| Paused when hidden | `launchOnView` for lists/dashboards |
| Idempotent | Restarted collectors must not repeat side effects |
