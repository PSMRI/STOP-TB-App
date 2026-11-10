---
name: release-build
description: Handle STOP-TB versioning, CI and distribution — bump version.properties, run the Build & Distribute workflow (Firebase, Intune, Play Internal), promote a build to Intune, or edit GitHub Actions/fastlane. Use when the user says bump version, release, distribute, build UAT/prod APK, or changes .github/ or fastlane/. Run release-readiness first for a real release.
---

# Release Build

## Versioning

- `version/version.properties`: `VERSION` (name, e.g. `1.0.3`) and `VERSIONCODE` (positive integer). Read by `versioning.gradle`.
- `VERSIONCODE` must be higher than the last build uploaded to Play.
- The `distribute.yml` workflow takes `versionName`/`versionCode` as inputs, so a local bump is only needed when the user wants it committed.

## Workflows (`.github/workflows/`, release-2.2 and later — not on 1.1–2.1)

| Workflow | Use |
|---|---|
| `distribute.yml` — *StopTB Build & Distribute* | Manual. Inputs: `environment` (uat/prod), `signing` (debug/signed), Firebase / Intune / Play checkboxes, `branch`, `versionName`, `versionCode`, `deployTo` (QA-Group-1/2), `description` |
| `promote-intune.yml` — *StopTB Push to Intune* | `list` the Firebase APK catalog, `push` an exact signed APK to the matching Intune tenant/group |

Rules enforced by the workflows: Play Internal = prod + signed AAB only; Intune = signed only; debug builds go to Firebase testers only.

**`branch` input:** on every branch, `distribute.yml` defaults to `'release-2.3'`. Always pass `-f branch=<the branch being released>` explicitly and confirm it with the user — otherwise a run builds 2.3.

Trigger from the CLI when asked:

```bash
gh workflow run distribute.yml --ref <branch> \
  -f environment=uat -f signing=signed -f distributeFirebase=true \
  -f distributeIntune=false -f distributePlay=false \
  -f branch=<branch> -f versionName=1.0.4 -f versionCode=5 -f deployTo=QA-Group-1 \
  -f description="<release notes>"
```

Always confirm branch, environment, signing and destinations with the user before running — prod and Intune pushes reach real devices.

## Editing CI / fastlane

- Secrets come from GitHub secrets (`GOOGLE_SERVICES_JSON_*`, `FIREBASE_*`, `ENCRYPTED_PASS_KEY`, `ABHA_*`, `BASE_*_URL`, `CHAT_URL`, `AZURE_*`, `INTUNE_GROUP_*`). Never inline them; add new ones to `.env.example` and the README secrets list.
- Helper scripts: `.github/scripts/fetch_firebase_apk.py`, `upload_intune_android_lob.py`.
- `fastlane/Fastfile` drives Firebase/Play uploads — keep lane names stable; workflows call them.
- Propose CI/fastlane changes and wait for OK; edit with the Edit tool.
- After changes, validate YAML and run `./gradlew :app:assembleStoptbUatDebug` and `:app:assembleStoptbDebug`.

## Known gap

`README.md` describes an `android-ci.yml` (compile + unit tests + lint on PRs to `main`/`develop`), but the file isn't in `.github/workflows/` on any branch. If the user wants PR checks, recreate it running `assembleStoptbUatDebug`, `testStoptbUatDebugUnitTest` and `lintStoptbUatDebug` and uploading reports.
