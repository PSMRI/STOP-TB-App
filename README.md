# StopTB

`StopTB` is an Android app for beneficiary registration, TB screening, general examination, referral, suspected TB tracking, confirmed TB follow-up, and offline-first field workflows.

Current app version:
- `Version Name`: `1.0`
- `Version Code`: `1`

## App Variants

- `stoptb` -> `org.piramalswasthya.stoptb.prod`
- `stoptbUat` -> `org.piramalswasthya.stoptb.uat`

Display names:
- `StopTB`
- `StopTB-UAT`

## Main Modules

- `All Beneficiaries`
- `Beneficiary Registration`
  - Registration details
  - Anthropometry
  - ABHA
- `Tuberculosis`
  - `TB Screening`
  - `Diagnostics`
  - `Suspected TB`
  - `TB Confirmed`
- `General Examination`
- `General OPD`
- `Referrals`
  - `Digital Chest X-ray`
  - `True NAT`
  - `Health and Wellness Centre`
  - `Liquid Culture`
- `ABHA`
- `Camp Mode`
- `Service Location / Village Selection`
- `Sync Dashboard`

## Current Functional Flow

### Common Login Flow

1. Login
2. Select village/service location
3. Open `Home`

### Registration Officer Flow

1. Register beneficiary
2. Submit `Anthropometry Screen`
3. Return to beneficiary list
4. ABHA can be generated from the beneficiary card when allowed

### Nurse Flow

1. Open beneficiary list
2. Submit `General Examination`
3. Submit `TB Screening`
4. Submit or skip `General OPD`
5. Submit `Diagnostics`
6. Continue tracking from `Suspected TB` and `TB Confirmed` modules

In the beneficiary list, `General OPD` is shown after `TB Screening` is complete. The OPD button is red when OPD is pending and green when OPD is filled.

### Camp Mode Flow

1. Enable `Camp Mode` from login
2. Connect to a camp hub using QR scan or hub URL
3. The app verifies the hub health endpoint before camp login
4. If the hub disconnects later, the app checks connectivity before hitting camp APIs

## Key Features

- Secure login with role-based access
- Splash screen on app launch
- Offline-first local storage using Room
- Beneficiary registration with village and sub centre
- Camp mode for local hub based field operation
- TB workflow support:
  - general examination
  - screening
  - general OPD
  - diagnostics
  - suspected TB
  - confirmed TB
- Referral tracking
- Multilingual support:
  - English
  - Hindi
  - Assamese
- Background sync using WorkManager
- CI workflow for compile, unit test, and lint
- UAT distribution workflow through Firebase App Distribution

## Tech Stack

- `Kotlin`
- `XML`
- `Room`
- `MVVM`
- `Navigation Component`
- `Hilt`
- `Retrofit + Moshi/Gson`
- `WorkManager`
- `Firebase`

## Android Configuration

- `compileSdk`: `35`
- `targetSdk`: `35`
- `minSdk`: `25`

## Project Setup

1. Open project in Android Studio.
2. Sync Gradle.
3. Select build variant:
   - `stoptbDebug`
   - `stoptbRelease`
   - `stoptbUatDebug`
   - `stoptbUatRelease`
4. Add required config files like `google-services.json` in the correct source set if needed.
5. Build and run on device/emulator.

## CI/CD

GitHub Actions workflows are available under `.github/workflows`.

- `android-ci.yml`
  - Runs on pull requests and pushes to `main` or `develop`
  - Compiles `stoptbUatDebug`
  - Runs unit tests
  - Runs Android lint
  - Uploads test and lint reports as workflow artifacts
- `distribute-uat.yml`
  - Builds the UAT debug APK
  - Uploads the APK as an artifact
  - Distributes the APK to Firebase App Distribution

Required GitHub secrets for CI/CD:

- `GOOGLE_SERVICES_JSON_UAT`
- `GOOGLE_SERVICES_JSON_PROD`
- `FIREBASE_APP_ID_UAT`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `ENCRYPTED_PASS_KEY`
- `ABHA_CLIENT_SECRET`
- `ABHA_CLIENT_ID`
- `BASE_TMC_URL`
- `BASE_ABHA_URL`
- `ABHA_TOKEN_URL`
- `ABHA_AUTH_URL`
- `CHAT_URL`

Do not commit private files or credentials to this public repository. Sensitive files are ignored through `.gitignore`, including `google-services.json`, keystore files, service-account JSON files, `.env`, and `keystore.properties`.

## Important Files

- App manifest:
  - [AndroidManifest.xml](D:/FLW Volenteer/NikshayMitra/app/src/main/AndroidManifest.xml)
- App version:
  - [version.properties](D:/FLW Volenteer/NikshayMitra/version/version.properties)
- Build config:
  - [build.gradle](D:/FLW Volenteer/NikshayMitra/app/build.gradle)

## Notes

- Launcher activity is `LoginActivity`.
- Splash theme is configured on launcher startup.
- Active home flow uses `VolunteerActivity`.
- Diagnostics data is saved into TB suspected flow and is used for suspected/confirmed TB tracking.
