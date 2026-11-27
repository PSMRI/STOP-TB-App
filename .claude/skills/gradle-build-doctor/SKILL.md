---
name: gradle-build-doctor
description: Diagnose and fix STOP-TB build problems — kapt/Hilt/Room/Moshi annotation-processing errors, flavor-specific compile failures, dependency conflicts, NDK/native-lib issues, R8 failures and slow Gradle builds.
---

# Gradle Build Doctor (STOP-TB)

## Project facts
- Single module `:app`; Groovy `build.gradle`; versioning in `versioning.gradle`
- Flavors (dimension `default`): `stoptb`, `stoptbUat`; build types `debug`, `release`
- kapt for Hilt, Room 2.6.1, Moshi codegen; DataBinding + ViewBinding on
- Native code: `app/src/main/cpp/native-lib.cpp` (CMake, ABIs armeabi-v7a/arm64-v8a/x86/x86_64)
- Secrets plugin + `local.properties` / `keystore.properties` (never print their contents)

## Fast commands
```bash
./gradlew :app:compileStoptbDebugKotlin --console=plain -q 2>&1 | grep -E "^e: |error:|FAILED" | head -60
./gradlew :app:compileStoptbUatDebugKotlin --console=plain -q
./gradlew assembleStoptbDebug
./gradlew :app:dependencies --configuration stoptbDebugRuntimeClasspath > <scratchpad>/deps.txt
./gradlew assembleStoptbDebug --scan        # only with the user's OK (uploads build data)
./gradlew --stop                             # stuck daemons
```
Write full logs to the scratchpad and grep them; don't dump them into chat.

## Reading kapt errors
The real error is usually the **first** `e:` / `error:` line; the rest are follow-ons.
- `[Hilt] ... cannot be provided without an @Provides` → missing binding/module or wrong qualifier
- Room `error: There is a problem with the query` / `Cannot figure out how to save this field` → SQL typo, missing TypeConverter, column name mismatch
- `Unresolved reference: databinding` / `BR` → an XML layout error breaks DataBinding generation; find the layout named in the first error
- Moshi `Failed to find the generated JsonAdapter` → missing `@JsonClass(generateAdapter = true)` or kapt didn't run

## Other problems
- Duplicate class / version conflict → read the dependency tree; prefer aligning versions over `exclude`
- R8 errors in release → `missing_rules.txt` in `app/build/outputs/mapping/...`; add keep rules to `proguard-rules.pro`
- Slow builds → check `gradle.properties` (`org.gradle.jvmargs`, `org.gradle.caching`, `org.gradle.parallel`, `kapt.incremental.apt`); suggest changes, don't apply without OK

## Rules
- Fix the cause, not the symptom (no `@Suppress`, no disabling checks).
- Propose Gradle file changes and wait for OK; edit with the Edit tool.
