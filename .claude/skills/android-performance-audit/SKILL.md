---
name: android-performance-audit
description: Investigate slow screens, janky lists, slow tab switches, slow card opening or main-thread blocking in the STOP-TB app. Measures first, traces UI → ViewModel → repository → Room/API, and documents evidence before any code change.
---

# Android Performance Audit (STOP-TB)

Goal: find the *measured* bottleneck. Do not edit code during the audit.

## Project facts
- Packages: `org.piramalswasthya.stoptb` (stoptb), `org.piramalswasthya.stoptb.uat` (stoptbUat)
- Launcher: `.ui.splash.SplashActivity` (use as `<pkg>/.ui.splash.SplashActivity`)
- UI: Fragments + XML/DataBinding/ViewBinding, Hilt ViewModels, Paging 3, Room 2.6.1, WorkManager, Retrofit (Moshi/Gson)
- DB: `InAppDb` (`database/room/InAppDb.kt`), DAOs in `database/room/dao/`

## Steps

1. **Define the journey and the symptom.** Exact screen, action, data size (number of beneficiaries/households), device, build flavor. Ask the user if unknown.

2. **Trace the code path** (read-only): Fragment/Activity event → ViewModel → repository → DAO / API → back to UI state. Note for each hop:
   - which dispatcher/thread it runs on (`Dispatchers.IO`, `viewModelScope`, `lifecycleScope`, `runBlocking`)
   - every DB query and API call, and how many times it runs
   - Flow collectors (`collect`, `asLiveData`, `stateIn`) and when they re-emit
   - adapter work: `notifyDataSetChanged`, DiffUtil, binding inflation, image loading

3. **Capture runtime evidence** (debug build on a real device if possible):
   ```bash
   adb shell am force-stop <pkg>
   adb shell am start-activity -W -n <pkg>/.ui.splash.SplashActivity   # cold start TotalTime
   adb shell dumpsys gfxinfo <pkg> reset                             # then do the journey
   adb shell dumpsys gfxinfo <pkg>                                   # janky frames, percentiles
   adb logcat -v time | grep -E "Choreographer|Skipped .* frames|StrictMode|<tag>"
   ```
   - Perfetto system trace for the journey:
     `adb shell perfetto -o /data/misc/perfetto-traces/trace.pftrace -t 15s sched freq gfx view am wm dalvik database`
     then `adb pull /data/misc/perfetto-traces/trace.pftrace` and open at ui.perfetto.dev.
   - Suggest temporary `Timber.d` timing logs (`SystemClock.elapsedRealtime()`) around suspect calls only with the user's OK, and list them so they are removed later.
   - CPU/memory: `adb shell dumpsys meminfo <pkg>`, `adb shell top -n 1 | grep stoptb`.

4. **Classify findings**: main-thread DB/IO, repeated queries / N+1, unindexed queries, over-emitting Flows, heavy layout inflation, synchronous JSON parsing, WorkManager sync contending with UI reads, missing paging.

5. **Report** (no fix yet): table of `finding | evidence (number/trace) | file:line | suspected cause | proposed minimal fix | risk`. Rank by measured impact. Hand off to `/room-sqlite-optimization`, `/android-architecture-review` or `/performance-rca-report` as needed.

## Rules
- No measurement → no claim. Mark anything unmeasured as "hypothesis".
- Propose the fix plan and wait for approval; then edit with the Edit tool, one file at a time.
- Keep fixes minimal; do not change flows or existing UI strings.
- Re-measure the same journey after the fix and report before/after.
