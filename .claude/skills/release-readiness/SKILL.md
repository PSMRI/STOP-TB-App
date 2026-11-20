---
name: release-readiness
description: Pre-release checklist for a STOP-TB build — diff review against the previous release, DB migration safety, R8/ProGuard keep rules, version bump, flavor/signing, release-only crash risks, and a manual smoke-test list. Read-only report.
---

# Release Readiness (STOP-TB)

Read-only. Produce a go / no-go report; don't change files or run state-changing git commands.

## 1. What's in the release
```bash
git fetch
git log --oneline <previous-release-tag-or-branch>..HEAD
git diff --stat <previous>..HEAD
```
Group the changes by feature/ticket and flag risky areas (DB, sync workers, login/auth, encryption, shared CFU/Community/Occupation code).

## 2. Checks
- [ ] **DB**: `InAppDb` version bumped once per schema change; every bump has a registered `MIGRATION_x_y`; no destructive fallback; upgrade tested from the last released build (`/room-migration`)
- [ ] **R8/ProGuard**: new Moshi/Gson DTOs, Room entities, Parcelables, enums used by name and reflection are kept in `app/proguard-rules.pro`; a release build has been run and smoke-tested
- [ ] **Version**: versionCode/versionName from `versioning.gradle` / `version` incremented
- [ ] **Flavor/config**: correct flavor (`stoptb` vs `stoptbUat`), base URLs, Firebase project, remote-config defaults (`res/xml/remote_config_defaults.xml`)
- [ ] **Signing**: release signing config present (don't print keystore values)
- [ ] **Debug leftovers**: no temporary `Timber.d` timing logs, test hacks, hardcoded credentials/URLs, `TODO` workarounds in the diff
- [ ] **Logging**: no personal/health data logged (Timber trees include `CrashlyticsTree` and `SyncLogTree`)
- [ ] **Translations**: new strings exist in `values`, `values-hi`, `values-as` (`/localization-check`)
- [ ] **Permissions/manifest**: new permissions, exported components, `targetSdk` behaviour changes
- [ ] **CI**: `.github/workflows/distribute.yml` / `promote-intune.yml` inputs still valid

## 3. Smoke test (release or UAT build)
Fresh install + login; upgrade over the previous release with unsynced data present; open every home card; register a beneficiary; submit a form; sync online; go offline, create data, come back online and sync; kill and relaunch; language switch (en/hi/as).

## Output
Checklist with pass/fail/unknown, blocking issues first, then the smoke-test steps for QA.
