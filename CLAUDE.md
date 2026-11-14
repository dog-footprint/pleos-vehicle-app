# CLAUDE.md

dog-footprint: an Android Automotive app for the **Pleos Connect** platform (HMG vehicle app contest). A dog profile
drives climate guidance, break reminders, safety checks and a calming mode; every drive is logged as a "footprint".
Contest rules, deadlines, SDK coordinates and permissions are in `docs/CONTEST.md`.

## Build

- Always run `source scripts/env.sh` first. The system JDK is 26, which AGP 8.13 cannot use; the script pins JDK 21.
- `./gradlew installDebug` / `testDebugUnitTest` / `lintDebug`
- `./gradlew exportContestApk` builds the signed release and copies it to `apk/<team>_<app>_release.apk`. Needs `keystore.properties`.
- `navigation-compose` must stay on 2.9.x and `kotlinx-serialization-json` on a Kotlin 2.2 build (1.9.x): newer ones require compileSdk 37 / Kotlin 2.4.

## Emulator

- AVD `Pleos_Connect` (image `system-images;android-34;pleos_car_emulator;arm64-v8a`, 2560x1440). Start: `emulator -avd Pleos_Connect`.
- Apps run as **user 10**: `pm grant --user 10 …`; `run-as --user` is unsupported, so demo data is loaded with `scripts/demo-seed.sh` (debug-only intent extra `seed_b64`).
- Vehicle signals: Pleos Virtual Console on port 8088 (`scripts/vehicle-sim.sh forward`, then `gear|odometer|brake|hvac|window|door|seat|state`). Writes need the browser heartbeat the script sends. Catalog: `GET /vehicle/catalog`.
- `scripts/tap-text.sh "텍스트"` taps a Compose element by its text (uiautomator dump); use it instead of guessing coordinates.
- Screenshots: `adb exec-out screencap -p > file.png`. The app pane is the right ~2/3 of the screen.
- Known emulator facts: no `TRIP_DRIVING_TIME` / `DrivingMode`; HVAC areas are multi-seat (see README); ROW_2_LEFT occupancy is inverted vs the console's `rear_left`; window position is 0–10; Pleos TTS/Navi/Location connect fine with the `.env` credentials.

## Design (Pleos)

- Use `ui/pleos/` only: `Pleos.colors`, `PleosType`, `PleosDims`, `PleosButton`/`PleosToggleButton`/`PleosIconButton`/`PleosListRow`/`PleosSwitch`/`PleosPopup`/`PleosStatusLine`. Do not reach for Material cards, chips, elevation or `MaterialTheme.colorScheme` directly.
- Token values come from the official Figma files (see `scripts/pleos-tokens/README.md`); add new values there with their source, don't invent them.
- Driving rule: while `state.moving` a screen shows one number/one status/at most two buttons, no text entry, no long lists. Warnings are icon + text + fixed position, never colour alone. Icons are the 48 px outline set from `scripts/fetch-icons.sh` (`ic_<material_symbol>.xml`); no emoji in UI strings.
- Home has three states (parked / driving / `petAlone`); keep them distinct when adding features.

## Conventions

- Pleos SDK versions are pinned in `gradle/libs.versions.toml`; use the `libs.bundles.pleos` bundle.
- Pleos SDKs are callback based. `pleos/VehicleSource.vehicleFlow` wraps get-once + register/unregister in a `callbackFlow`; add new signals there, then collect them in `CareEngine` via `watch("label", flow) { … }` so failures are logged with a label.
- SDK callback values are platform-nullable; nulls are dropped in `vehicleFlow`. Missing permissions throw `SecurityException` synchronously (handled by `registerOrFail`).
- HVAC/seat areas must come from the capability APIs (`hvacCurrentTempAreas()`, `hvacTempBounds()`, `hvacFanSpeedBounds()`, `hvacPowerAreas()`); `CareEngine.watchZone` picks the area containing the dog's seat, else a front-seat fallback (`zoneTempFallback`).
- Care rules live in `care/ComfortRules.kt` as pure functions with constants at the top; change thresholds there and keep `ComfortRulesTest` green. `CareEngine` only orchestrates (1 s tick, trip life-cycle, events, guidance).
- Every constant in `ComfortRules.kt` traces to a cited source in `docs/CARE_RULES.md`. Do not add or move a threshold without adding the evidence there first; if no source exists, say so in the doc rather than guessing.
- Life stage comes from `DogProfile.lifeStage()` (birth date + size, AAHA thresholds), not from `ageGroup` directly; pass the stage into rule functions.
- Seams for not-yet-approved SDK features: `ClimateActuator` (`GuidanceOnlyActuator` default, `PleosHvacActuator` behind `Settings.controlEnabled`), `VoiceGuide` (`CompositeVoiceGuide` = Pleos TTS → chime).
- UI text goes through string resources (`values/` Korean, `values-en/` English); enums map to labels in `ui/Format.kt`.
- `pleos.car.permission.FUSED_LOCATION` is a runtime (dangerous) permission; the others are install-time. Declare only what the app uses; `CONTROL_CAR_*` must be dropped before an App Market submission unless approved.
- minSdk stays at 28 or higher (Pleos signing requirement). Avoid the deprecated Gear APIs except in `VehicleSource.gear()`, which is the only PARK signal available.
- `.env` holds Playground credentials and is gitignored; never commit it or echo its values.
