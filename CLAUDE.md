# CLAUDE.md

This is an Android Automotive app for the **Pleos Connect** platform, built for the HMG vehicle app contest.
Contest rules, deadlines, SDK coordinates and permissions are in `docs/CONTEST.md`. Read it before changing the SDK setup.

## Build

- Always run `source scripts/env.sh` first. The system JDK is 26, which AGP 8.13 cannot use; the script pins JDK 21.
- `./gradlew assembleDebug` / `installDebug` / `lintDebug`
- `./gradlew exportContestApk` builds the signed release and copies it to `apk/<team>_<app>_release.apk`. It needs `keystore.properties`.

## Emulator

- AVD `Pleos_Connect` uses image `system-images;android-34;pleos_car_emulator;arm64-v8a` (Pleos Connect v2.1.0, 2560x1440). Start it with `emulator -avd Pleos_Connect`.
- The emulator shows a vehicle panel on the left and runs apps on the right. Use `adb exec-out screencap -p` for screenshots.

## Conventions

- Pleos SDK versions are pinned in `gradle/libs.versions.toml`. Get them through the `libs.bundles.pleos` bundle.
- Pleos SDKs are callback based. Wrap each signal in a `callbackFlow` in `pleos/`: call get-once, then register, and unregister in `awaitClose`. ViewModels collect these flows.
- SDK callback values are platform-nullable (`Float?`, `Int?`, `LocationData?`). Null-check them.
- Every Vehicle domain needs its `pleos.car.permission.CAR_*` permission in the manifest. These are not runtime permissions. Declare only what the app uses, because App Market review checks this.
- `pleos.car.permission.FUSED_LOCATION` is a runtime (dangerous) permission, so request it before using FusedLocation. A missing permission makes SDK calls throw `SecurityException` synchronously, so wrap registration in a `try` (see `registerOrFail`).
- minSdk must stay at 28 or higher (Pleos signing requirement).
- To check an SDK's real API surface, download its AAR from the Pleos Nexus and run `javap` on it. The online docs leave out signatures.
- Avoid deprecated Gear APIs (`getCurrentGearSelectionState` and related calls).
