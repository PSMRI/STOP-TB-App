---
name: adb-device-debug
description: ADB toolkit for debugging STOP-TB on a device or emulator — install/launch, filtered logcat, pull the debug Room DB, clear data, inspect WorkManager/jobs, memory, screenshots and UI hierarchy dumps.
---

# ADB Device Debugging (STOP-TB)

## Project facts
- Packages: `org.piramalswasthya.stoptb` (stoptb), `org.piramalswasthya.stoptb.uat` (stoptbUat)
- Launcher: `.ui.splash.SplashActivity`
- DB file: `databases/Sakhi-2.0-In-app-database` (plain SQLite on debug builds, SQLCipher on release)
- `run-as` only works on debuggable builds.

## Commands
```bash
PKG=org.piramalswasthya.stoptb
adb devices -l

# build / install / launch
./gradlew assembleStoptbDebug
adb install -r app/build/outputs/apk/stoptb/debug/*.apk
adb shell am start-activity -W -n $PKG/.ui.splash.SplashActivity
adb shell am force-stop $PKG

# logs (app process only)
adb logcat -c
adb logcat -v time --pid=$(adb shell pidof -s $PKG)
adb logcat -v time *:E

# database (debug build)
adb shell run-as $PKG ls databases
adb exec-out run-as $PKG cat databases/Sakhi-2.0-In-app-database > <scratchpad>/app.db

# shared prefs
adb shell run-as $PKG ls shared_prefs

# background work
adb shell dumpsys jobscheduler | grep -A5 $PKG
adb shell cmd jobscheduler run -f $PKG <jobId>

# memory / CPU / frames
adb shell dumpsys meminfo $PKG
adb shell dumpsys gfxinfo $PKG
adb shell top -n 1 | grep stoptb

# UI
adb exec-out screencap -p > <scratchpad>/screen.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml <scratchpad>/
adb shell input tap <x> <y>

# network / process death
adb shell svc wifi disable; adb shell svc data disable     # offline test
adb shell am kill $PKG                                     # simulate process death (app in background)

# reset (DESTRUCTIVE: wipes unsynced data on the device)
adb shell pm clear $PKG
```

## Rules
- Ask before `pm clear`, uninstall, or anything else that deletes app data on the device.
- Save pulled DBs, screenshots and logs in the session scratchpad, never in the repo.
- Use test accounts and synthetic data only.
