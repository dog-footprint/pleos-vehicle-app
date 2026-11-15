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
- Driving rule: while `state.moving` a screen shows one number/one status/at most two buttons, no text entry, no long lists. Warnings are icon + text + fixed position, never colour alone. Icons are the 48 px outline set from `scripts/fetch-icons.sh` (`ic_<material_symbol>.xml`); no emoji in UI strings. The dog's picture is the breed line-art `breed_<key>.xml` (SVG Repo CC0 outline set; sources and look-alikes in `scripts/breed-icons/sources.txt`), shown through `ui/DogAvatar` wherever the dog is named — the platform has no photo import.
- Restraint: the buckle sensor only sees a harness clip, so `restraintOk(beltBuckled, restraintConfirmed)` also accepts the driver's checklist confirmation (`confirmRestraint`), reset at trip end / unboard.
- Home is four separate screens, not one layout with flags: `ParkedHome`, `DrivingHome`, `PetAloneHome`, `TripEndCheckIn` (`ui/HomeScreen.kt`). Driving shows one number, one status line, the warning slot and ≤ 2 buttons — nothing that ticks per second; temperatures are whole degrees, times are minutes.
- Warnings go through `CareState.alerts` (`care/ComfortRules.activeAlerts`, DANGER > CAUTION) into the fixed 112 px `AlertSlot` in `ui/AppNav.kt`; transient notices use `banner` (4 s). Never add a second banner or move content when a warning appears.
- The only user action is "태우기" (`board`) / "내리기" (`unboard`); trips start and end from vehicle signals. Everything trip-scoped resets through `CareState.afterTripEnd()` (unit-tested).
- Navigation is the vertical GNB (140 wide, 124 buttons) from the display guide; the app stacks to one column below `PleosDims.compactMaxWidth`.
- Leaving the car is two stages (`CareEngine` tick): the P shift with a risky cabin raises `LEAVE_PREP` + a voice line; the driver's door opening shows `PreExitHome` (`isPreExit`); after the door closes there is a `PET_ALONE_CONFIRM_MS` grace (driving off or shifting cancels, a passenger in ROW_1_RIGHT blocks) before `PetAloneHome`. The SDK has no ignition/power-state read and no utility-mode climate API — do not promise either.
- The one climate button on the leaving / pet-alone screens comes from `ComfortRules.climateAction` (off → turn on; hot → lower to the band centre; cold → raise), rendered by `climateActionLabel` / `climateActionGuide` (release). Climate off is a CAUTION inside the band and a DANGER outside or trending out (`hvacOffLevel`, 2-minute trend); `CareState.cabinLevel` carries it to the UI.
- Durations the driver reads all go through `ComfortRules.wholeMinutes` (`durationText` = `minutesText`); a trip under 1 km and under 3 min is discarded at `endTrip` (`isTripWorthRecording`), so parking-lot moves never reach Footprints.
- Alert wording lives in `care/AlertTexts.kt` keyed by `AlertContext`; the slot says the action, the body shows the verdict. Danger = negative border + filled icon badge, caution = textPrimary left bar + triangle, notice = plain. `accent` is only for selection and the primary action.
- Demo tooling (debug builds): `DemoReceiver` broadcast actions (`cold|hot|hvac_off|clear|break|brake|belt_on|belt_off|pet_alone|record_on|...`), `scripts/demo-seed.py` (history relative to now), `scripts/record-demo.sh OUT [--from-scene N] [--to-scene M]`, `scripts/make-demo-video.sh IN OUT --voice synth` (voice is synthesized: the emulator capture lacks the navigation bus), `scripts/check-demo.py`. The console ignores an odometer that goes backwards — scripted drives continue from `GET /vehicle/odometer`. Night mode must be forced with `cmd car_service day-night-mode night` or the car policy flips the theme mid-take; the status-bar clock is moved to ~14:00 with `cmd alarm set-timezone Etc/GMT∓N` (restored on exit) and `demo-seed.py --utc-offset=N` places the seeded trips in that zone.

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
