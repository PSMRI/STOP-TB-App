---
name: health-data-security-review
description: Security and privacy review for STOP-TB, which stores patient health data offline — PII/PHI in logs, SQLCipher key handling, local storage, exported components, network security, backups, tokens and secrets. Read-only report.
---

# Health Data Security Review (STOP-TB)

Defensive review only. Report findings; propose fixes; don't edit without approval.

## Scope
The current diff (`git diff main...HEAD`) by default, or the area the user names.

## Checklist
**Logging**
- Timber trees: `DebugTree` (debug), `SyncLogTree`, `CrashlyticsTree` (in `SakhiApplication`). Flag any log of names, phone numbers, Aadhaar/ABHA/Nikshay IDs, addresses, diagnoses, tokens or full request/response bodies, especially ones that reach Crashlytics or the sync log in release.
- OkHttp logging interceptor level in release builds.

**Local data**
- DB encryption: release builds use SQLCipher via `DatabaseKeyManager` + `RoomDbEncryptionHelper`; debug builds are plain. Check the key is in the Android Keystore / encrypted storage and never hardcoded, logged or sent anywhere.
- `crypt/CryptoUtil.kt`: algorithm, mode, IV handling, hardcoded keys.
- SharedPreferences holding tokens or PII unencrypted.
- Files (photos, PDFs, exports) in external storage; `requestLegacyExternalStorage`; `FileProvider` paths (`res/xml/provider_paths.xml`) not too broad.

**Platform**
- `AndroidManifest.xml`: `exported="true"` components with no permission, intent-filter data taken without validation, PendingIntent mutability.
- `res/xml/network_security_config.xml`: cleartext traffic, user CAs trusted in release, pinning.
- `backup_rules.xml` / `data_extraction_rules.xml`: DB and prefs excluded from cloud/device-transfer backup.
- WebView: JavaScript interfaces, file access.

**Secrets**
- API keys, credentials or URLs with tokens in source, `google-services.json` handling, `local.properties` / `keystore.properties` never committed (`.gitignore`).

**Auth/session**
- Token refresh/expiry, logout clears tokens and (per product decision) local data, no auth bypass in debug flags shipped to release.

## Output
`severity | file:line | issue | exploit/leak scenario | fix`. Never include real patient data or secrets in the report.
