---
name: memory-leak-check
description: Find memory leaks and memory bloat in STOP-TB — leaked Fragments/Activities/Views, binding references not cleared, adapter and listener leaks, Flow collectors outliving their lifecycle, large bitmaps — using code review, dumpsys meminfo and heap dumps.
---

# Memory Leak Check (STOP-TB)

## 1. Static review of the target screens
- `_binding` not set to `null` in `onDestroyView()` (Fragments with ViewBinding/DataBinding)
- RecyclerView adapter held by the Fragment after `onDestroyView`; `recyclerView.adapter = null` missing where the adapter holds view references
- Listeners/callbacks registered with an Activity/Fragment and never removed (TextWatchers on long-lived objects, location callbacks, broadcast receivers)
- `@Singleton` / object / companion fields holding a `Context`, `View`, `Activity` or Fragment
- Flows/LiveData observed with `this` instead of `viewLifecycleOwner` in Fragments; `lifecycleScope.launch { flow.collect {} }` without `repeatOnLifecycle`
- Handlers/`postDelayed` runnables or coroutine jobs still referencing views after destroy
- Large bitmaps (photos, signatures, documents) decoded at full size or cached without limits
- Dialogs not dismissed on destroy (`WindowLeaked` in logcat)

## 2. Runtime check (debug build)
```bash
PKG=org.piramalswasthya.stoptb
adb shell dumpsys meminfo $PKG          # note Java heap, Views, Activities counts
# repeat the journey 5-10x (open screen → back), then:
adb shell dumpsys meminfo $PKG          # Activities/Views should return to baseline
adb shell am dumpheap $PKG /data/local/tmp/app.hprof
adb pull /data/local/tmp/app.hprof <scratchpad>/
```
Open the `.hprof` in the Android Studio Profiler (File → Open) and look for retained `Fragment`/`Activity` instances with `mDestroyed=true`.

## 3. Optional: LeakCanary
Not in the project. If the user agrees, add only as `debugImplementation 'com.squareup.leakcanary:leakcanary-android:<version>'` (no code needed). Remove it before merging unless the team wants to keep it.

## Output
`leaked object | retention path | file:line | fix`, plus before/after `meminfo` numbers after the journey loop.
