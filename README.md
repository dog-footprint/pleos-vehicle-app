# APPNAME

> One-line description of the app. (TODO)

An in-vehicle app for the Pleos Connect (Android Automotive OS) platform, built for the
[현대자동차그룹 차량용 앱 개발 공모전](https://appdevchallenge.auto-hmg.io/).
Contest rules, SDK list and deadlines are summarized in [docs/CONTEST.md](docs/CONTEST.md).

## APK

- Location: [`apk/TEAMNAME_APPNAME_release.apk`](apk/) (or the GitHub Release link: TODO)
- Build: release build, signed, minSdk 28 / targetSdk 35

## How to run

1. Start a **Pleos Connect v2.1.0** AVD (see *One-time setup* below), for example `emulator -avd Pleos_Connect`.
2. Install the APK:
   ```bash
   adb install -r apk/TEAMNAME_APPNAME_release.apk
   ```
3. Open **APPNAME** from the launcher. Allow the location permission prompt on first launch.
4. To simulate vehicle signals (speed, battery, doors, …), use the emulator's
   *Extended controls → Car data* panel or the Pleos vehicle emulator
   ([guide](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)).

## Test environment

| | |
|---|---|
| Emulator | Pleos Connect system image v2.1.0 (API 34, Automotive) |
| Pleos SDKs | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3 |
| Build | AGP 8.13.2, Gradle 8.14.6, Kotlin 2.2.21, JDK 21 (bytecode target 17), Jetpack Compose |

---

## Development

### One-time setup

1. **Android Studio**: install it (`brew install --cask android-studio`) and point it at the existing SDK
   in `~/Library/Android/sdk`. In *Settings → Build Tools → Gradle → Gradle JDK*, choose JDK 21.
2. **Pleos emulator image**: in *Tools → SDK Manager → SDK Update Sites*, add a site named
   `Pleos Connect System Image` with this URL:
   - Apple Silicon: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Intel / Windows / Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

   Then, under *SDK Platforms*, check *Show Package Details* and install the
   **Pleos Connect** image under Android API 34. You will be asked to accept the 42dot SDK license.
3. **AVD**: open *Device Manager → Create*. Choose the **Automotive** form factor and the
   *Pleos Connect v2.1.0* image. Set RAM to 4 GB and Boot to Cold.
4. **Pleos Playground credentials**: copy `.env.example` to `.env` (gitignored) and fill in
   `PROJECT_ID`, `CLIENT_ID`, `CLIENT_SECRET` and `CRN` from the team's project at
   https://pleos.ai/playground/console/projects. The build exposes them as `BuildConfig.PLEOS_*`.
   `CRN` is only needed on a rootable devbox target (`scripts/set-emulator-crn.sh`); the stock
   Pleos Connect v2.1.0 AVD does not need it.
5. **Release key**: run `scripts/create-release-keystore.sh`. It writes `release.jks` and
   `keystore.properties`, both gitignored. Back up the key, because the app is tied to it.

### Build from the terminal

```bash
source scripts/env.sh        # pins JDK 21 + Android SDK on PATH (system JDK 26 is too new for AGP)
./gradlew installDebug       # build + install on the running emulator
./gradlew exportContestApk   # signed release → apk/<team>_<app>_release.apk
```

Set `contest.teamName` and `contest.appName` in `gradle.properties` to control the APK file name.

### Code layout

```
app/src/main/java/com/hmgchallenge/vehicleapp/
├── MainActivity.kt
├── pleos/                 # Flow-based wrappers around the Pleos SDKs
│   ├── VehicleSource.kt   #   Vehicle SDK (odometer, EV state of charge, …)
│   └── LocationSource.kt  #   FusedLocation SDK
└── ui/                    # Compose UI + ViewModels
```

Before submitting, rename the `com.hmgchallenge.vehicleapp` package and application ID to match
your Pleos Playground project.
