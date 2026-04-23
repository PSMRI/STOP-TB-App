# StopTB

`StopTB` is an Android app for TB screening, referral, suspected TB tracking, confirmed TB follow-up, and beneficiary management.

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
- `TB`
  - `TB Screening`
  - `TB Suspected`
  - `TB Confirmed`
- `Referrals`
  - `Digital Chest X-ray`
  - `True NAT`
  - `Health and Wellness Centre`
  - `Liquid Culture`
- `ABHA`
- `Service Location / Village Selection`
- `Sync Dashboard`

## Current Functional Flow

1. Login
2. Select village/service location
3. Open `Home`
4. Register beneficiary
5. Submit `TB Screening`
6. Submit or skip `Vital Screen`
7. Open `Diagnostics`
8. Save diagnostics data to `TB_SUSPECTED`
9. Continue tracking from `TB Suspected` and `TB Confirmed` modules

## Key Features

- Secure login with role-based access
- Splash screen on app launch
- Offline-first local storage using Room
- Beneficiary registration with village and sub centre
- TB workflow support:
  - screening
  - vitals
  - diagnostics
  - suspected TB
  - confirmed TB
- Referral tracking
- Multilingual support:
  - English
  - Hindi
- Background sync using WorkManager

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
- Home title click can open `ServiceLocationActivity`.
- Diagnostics data is used as prefill source for `TB Suspected`.
