---
name: android-regression-testing
description: Replay key STOP-TB user journeys on a device/emulator before and after a change, compare timings and correctness, and detect regressions. Includes optional Macrobenchmark setup guidance.
---

# Android Regression Testing (STOP-TB)

## Standard journeys
Agree with the user which apply to the change:
1. Cold start → login/splash → home
2. Open each home card (TB, Contact Tracing, Counselling, CFU, Camp forms, ...)
3. Switch tabs on the beneficiary/household lists
4. Search beneficiaries (name / ID / mobile)
5. Open a beneficiary → fill and submit a form → return to the list (check the list updates, no duplicate records)
6. Revisit a screen (back navigation, config change/rotation)
7. Trigger sync and check records reach the expected state (see `/offline-sync-debug`)

## Steps

1. **Baseline on the current code** (same device, same data, debug build):
   ```bash
   ./gradlew assembleStoptbDebug
   adb install -r app/build/outputs/apk/stoptb/debug/*.apk
   adb shell am start-activity -W -n <pkg>/.ui.splash.SplashActivity
   adb shell dumpsys gfxinfo <pkg> reset   # run the journey
   adb shell dumpsys gfxinfo <pkg>
   ```
   Repeat each timed journey 3–5 times and use the median. Record the device model, data size and build.

2. **Correctness checklist per journey**: right data shown, counts match the DB, no crash/ANR in `adb logcat *:E`, no duplicated API calls (check OkHttp/Timber logs), forms save and reload correctly, Hindi/Assamese still render.

3. **Apply the change, rebuild, and repeat the same journeys.**

4. **Compare**: table `journey | before median | after median | delta | correctness pass/fail`. A slowdown above ~10% or any correctness failure is a regression; report it with logs.

## Optional: Macrobenchmark
Only if the user asks (no test modules are added by default). Outline: a `:macrobenchmark` `com.android.test` module, a `benchmark` build type on `:app` (`initWith release`, debug signing, `profileable`), `StartupTimingMetric` / `FrameTimingMetric` tests per journey, then `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`. Traces are saved for Perfetto.

## Rules
- This skill itself doesn't add test classes; it does manual and timing checks. For new features, unit tests come from `/add-unit-test`; for bug fixes, only when the user asks.
- Report failures as they are, with the output.
