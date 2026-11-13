---
name: crash-anr-triage
description: Triage a STOP-TB crash or ANR from a Crashlytics stack trace, ANR trace, or logcat — map frames to source (including R8-obfuscated traces), find the root cause and propose a minimal fix.
---

# Crash / ANR Triage (STOP-TB)

## Input
A stack trace (Crashlytics, Play Console, logcat), the app version/flavor, device/OS, and how often it happens. Remove any patient data before pasting.

## Get traces from a device
```bash
adb logcat -b crash -d
adb logcat -v time *:E | grep -A30 "FATAL EXCEPTION"
adb bugreport scratch/bugreport.zip        # ANRs: FS/data/anr/ inside the zip
```

## Release (R8) traces
Release builds are minified. De-obfuscate with the matching mapping file:
`retrace app/build/outputs/mapping/<flavor>Release/mapping.txt trace.txt`
(`retrace` is in `$ANDROID_HOME/cmdline-tools/latest/bin`). Crashlytics de-obfuscates automatically if the mapping was uploaded.

## Common causes to check
- `ClassNotFound` / `NoSuchField` / Moshi/Gson `JsonDataException` only in release → missing ProGuard keep rules (`app/proguard-rules.pro`)
- `NullPointerException` on `binding` → used after `onDestroyView`, or async callback after the Fragment is detached
- `IllegalStateException: Fragment not attached` / navigation `action not found` → double navigation, navigating from a background callback
- Room `Migration didn't properly handle` → see `/room-migration`
- `SQLiteException: file is not a database` → SQLCipher key/encryption state (release only)
- WorkManager crashes in `doWork` → unhandled exceptions, Hilt worker factory
- ANR: main-thread DB/IO, `runBlocking`, lock contention with sync workers, slow `BroadcastReceiver` / `onCreate`

## Output
1. Crashing frame → file:line in this repo
2. Root cause and the exact sequence that triggers it
3. Minimal fix proposal (wait for approval)
4. How to reproduce and verify; whether other call sites have the same pattern
