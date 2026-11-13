---
name: verify-feature
description: Run the definition-of-done checks for a STOP-TB change — build, unit tests, lint, and migration checks when the DB changed. Use after review-work passes, before declaring any change done, or when the user asks to verify.
---

# Verify Feature

Evidence over claims: run the commands and report their results.

## Checks

| When | Command |
|---|---|
| Always (Kotlin/XML changed) | `./gradlew :app:compileStoptbDebugKotlin --console=plain -q` (fast) then `./gradlew :app:assembleStoptbUatDebug` |
| Always | `./gradlew :app:testStoptbUatDebugUnitTest` (runs the existing suite plus any new tests) |
| Layouts, manifest, resources changed | `./gradlew :app:lintStoptbUatDebug` |
| Room schema changed — **Style A** branch (`DB_VERSION` + `MIGRATIONS`, release-2.3/2.4) | `--tests "*InAppDbMigrationsTest"` in the unit run; confirm `app/schemas/.../<N>.json` is generated |
| Room schema changed — **Style B** branch (everything else) | No chain test or schema export exists: give the manual upgrade test from `room-migration` Step 3 (install previous build with unsynced data → `adb install -r` new build → open affected screens, watch logcat) |
| Room schema changed, device/emulator available | `./gradlew :app:connectedStoptbUatDebugAndroidTest` |
| Gradle / version / CI changed | also `./gradlew :app:assembleStoptbDebug` (prod flavor) |

If a command can't run here (no JDK/Android SDK, no device), say so plainly and list the exact commands for the user to run — never report them as passed.

## Report

```text
Verify:
| Check | Command | Result |
|-------|---------|--------|
```

Then numbered manual test steps for the changed screens, including regression checks on related flows (CFU / Community / Occupation if shared code was touched).

Failures → back to `fix-feature` / `bug-fix-workflow` or the owning skill, then re-verify. Do not commit unless asked.
