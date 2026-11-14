# dog-footprint

> 반려견의 견종·나이·자리에 맞춰 차량 실내 온도, 휴식 시간, 안전을 챙기고 매 드라이브를 "발자국"으로 기록하는 Pleos Connect 앱.
> An in-vehicle dog-care app for Pleos Connect: breed-aware climate guidance, break reminders, safety checks, a calming
> sound, and an automatic trip log ("footprints") — built for the
> [현대자동차그룹 차량용 앱 개발 공모전](https://appdevchallenge.auto-hmg.io/).

Contest rules, SDK status and deadlines: [docs/CONTEST.md](docs/CONTEST.md). Evidence behind every care threshold: [docs/CARE_RULES.md](docs/CARE_RULES.md).

## APK

- Location: [`apk/dog-footprint_dog-footprint_release.apk`](apk/) (or the GitHub Release link: TODO)
- Build: release build, signed (v3), minSdk 28 / targetSdk 35

## How to run

1. Start a **Pleos Connect v2.1.0** AVD (see *One-time setup* below), e.g. `emulator -avd Pleos_Connect`.
2. Install the APK:
   ```bash
   adb install -r apk/dog-footprint_dog-footprint_release.apk
   ```
3. Open **dog-footprint** from the launcher. Allow the location permission prompt on first launch.
4. Create a dog profile (프로필 → 이름 + 견종 + 자리 → 저장), then on 홈 turn on **탑승**. The trip starts
   automatically when the vehicle moves.
5. To simulate vehicle signals, use the Pleos **Virtual Console**: `adb forward tcp:8088 tcp:8088` then open
   http://127.0.0.1:8088/ — or script it with `scripts/vehicle-sim.sh` (see *Demo* below).

## Test environment

| | |
|---|---|
| Emulator | Pleos Connect system image v2.1.0 (API 34, Automotive, 2560×1440) |
| Pleos SDKs | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3 |
| Build | AGP 8.13.2, Gradle 8.14.6, Kotlin 2.2.21, JDK 21 (bytecode target 17), Jetpack Compose |

## What it does

| Feature | How | Pleos SDK |
|---|---|---|
| Dog profile → comfort band + break interval | breed presets (size, coat, flat-faced), birth date → AAHA life stage, weight & body condition, respiratory/heart issue, motion sickness, restraint, usual seat — thresholds sourced in [docs/CARE_RULES.md](docs/CARE_RULES.md) | — |
| Last-meal check at boarding | "when did the dog last eat?" popup; warns if under 2 h (motion sickness) | — |
| Cabin temperature at the dog's seat vs. its band, recommendation text | HVAC current/target temperature, fan, power | Vehicle (CAR_CLIMATE) |
| Auto-adjust climate (experimental, Settings toggle) | HVAC set-point / fan / direction, seat heater, window close when parked | Vehicle Control (not yet approved in Pleos Playground; works on the emulator) |
| Break reminders (pre-alert + due), rest-area suggestion | odometer-based driving time; NaviHelper destination + TBT rest area | Vehicle (CAR_DRIVE), NaviHelper |
| Safety checks: window open, harness unbuckled, door open, sport mode, hard-brake bursts | seat occupancy/belt, window & door position, drive mode, brake pedal | Vehicle |
| Spoken guidance (Korean) with chime fallback | Pleos TTS, registered with the team's Client ID/Secret | TextToSpeech |
| Calming mode | CC0 calm classical (Open Goldberg Variations, three pieces rotated) or a TTS-read story; brown noise dropped for lack of canine evidence | TextToSpeech (story) |
| Footprints: per-trip distance, time, breaks, events, comfort score, mood | local DataStore | FusedLocation for coordinates |

---

## Development

### One-time setup

1. **Android Studio**: `brew install --cask android-studio`, point it at the SDK in `~/Library/Android/sdk`, and choose
   JDK 21 under *Settings → Build Tools → Gradle → Gradle JDK*.
2. **Pleos emulator image**: *Tools → SDK Manager → SDK Update Sites*, add `Pleos Connect System Image` with
   - Apple Silicon: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Intel / Windows / Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

   then install the **Pleos Connect** image under Android API 34 (accept the 42dot SDK license).
3. **AVD**: Device Manager → Create → **Automotive** form factor, *Pleos Connect v2.1.0* image, 4 GB RAM, Cold boot.
   Name it `Pleos_Connect` so the scripts find it.
4. **Pleos Playground credentials**: copy `.env.example` to `.env` (gitignored) and fill in `PROJECT_ID`, `CLIENT_ID`,
   `CLIENT_SECRET` and `CRN` from https://pleos.ai/playground/console/projects. The build exposes them as
   `BuildConfig.PLEOS_*`; TTS/STT need the client credentials. `CRN` is only for a rootable devbox
   (`scripts/set-emulator-crn.sh`); the stock AVD does not need it.
5. **Release key**: `scripts/create-release-keystore.sh` writes `release.jks` + `keystore.properties` (both gitignored).
   Back the key up — the app identity is tied to it.

### Build from the terminal

```bash
source scripts/env.sh        # pins JDK 21 + Android SDK on PATH (the system JDK 26 is too new for AGP)
./gradlew installDebug       # build + install on the running emulator
./gradlew testDebugUnitTest  # care-rule tests
./gradlew exportContestApk   # signed release → apk/<team>_<app>_release.apk
```

### Demo / scripted verification

```bash
scripts/vehicle-sim.sh forward                  # once per emulator boot (adb forward 8088)
scripts/demo-seed.sh                            # debug builds: load scripts/demo-seed.json (코코 · 시베리안 허스키 · 뒷좌석 왼쪽)
scripts/vehicle-sim.sh gear Drive
scripts/vehicle-sim.sh odometer 12.5            # repeat with increasing values → "주행 중", trip starts if 탑승 is on
scripts/vehicle-sim.sh hvac "power=1&ac=1&fan=5&driver_temp=27&passenger_temp=27"
scripts/vehicle-sim.sh window rl 60             # → 창문 많이 열림
scripts/vehicle-sim.sh brake 100; scripts/vehicle-sim.sh brake 0   # ×4 within 10 s → 급제동 반복
scripts/vehicle-sim.sh seat rear_left true      # see the quirk below
scripts/tap-text.sh "여행 종료"                  # taps any on-screen text (uiautomator)
```

Settings → 데모 트리거 reproduces the same situations without the console.

### Demo video

[`docs/demo/dog-footprint-demo.mp4`](docs/demo/dog-footprint-demo.mp4) (3 min, 1080p, with the emulator's audio — the
Pleos TTS voice and the calming sound are real). It is produced entirely by scripts, so it can be re-recorded after any
change:

```bash
scripts/record-demo.sh build/demo        # night mode, cold-starts the app, seeds 코코, drives the scenario, records 2 segments + scenes.json
scripts/make-demo-video.sh build/demo docs/demo/dog-footprint-demo.mp4   # concat, Korean caption strip below the video, loudness-normalised audio
```

The emulator's recorder stops at 180 s per file, hence the two segments. Captions live in `record-demo.sh` next to each scene.

### Emulator facts worth knowing (verified on Pleos Connect v2.1.0)

- Apps run as **user 10** on AAOS: `run-as`/`pm grant` need `--user 10` (`run-as --user` is unsupported here, hence
  the seed intent in `MainActivity`).
- HVAC zones are multi-seat areas: current temperature is readable on `[ROW_1_LEFT, ROW_1_RIGHT, ROW_2_*]`, set-point on
  `[ROW_1_LEFT]`/`[ROW_1_RIGHT]`, fan on `[ROW_1_LEFT, ROW_1_RIGHT]`, power on the all-cabin area. `CareEngine.watchZone`
  resolves them from the SDK capability lists; never construct `SeatArea(ROW_2_LEFT)` for HVAC.
- `TRIP_DRIVING_TIME` and `DrivingMode` are not supported by the emulator (handled; driving time is counted in-app).
- Seat occupancy for ROW_2_LEFT reads as the **inverse** of the console's `rear_left` flag on this image.
- Window position is reported 0–10 by the SDK (console uses 0–100 %).
- The console only accepts writes while a browser-session heartbeat is alive; `vehicle-sim.sh` sends one before each write.
- Pleos TTS, NaviHelper and FusedLocation all connect on the emulator with the team's credentials, even though the
  Playground project lists them as "Not Available Yet".

### Design system

The UI follows the Pleos Connect design guide, with values read directly from the official Figma files
(`scripts/pleos-tokens/`): `ui/pleos/PleosTokens.kt` holds the Hyundai light/dark colours, type scale, spacing and
component sizes; `ui/pleos/PleosComponents.kt` implements the handful of Pleos components the app uses (basic / toggle /
icon buttons, list rows, switch, popup, notification strip). Rules applied on top of the guide:

- One glanceable fact per screen while driving — on 지금 it is the temperature at the dog's seat (`Driving/Speed` scale)
  plus a one-word verdict with an icon; driver side (left of the app window) carries the value and at most two buttons.
- Three states: parked (full UI), driving (summary only, no text entry, no long lists), **pet alone** (parked + 탑승 +
  driver's door opened and closed → cabin temperature, climate state and elapsed time, with spoken alerts).
- One accent (`informative/positive` blue) for state/actions, one warning colour (`negative` red) always paired with an
  icon and a fixed position; dark theme follows the vehicle's night mode. Icons are Material Symbols Outlined 48 px
  (`scripts/fetch-icons.sh`), a single thin-line set like Pleos'. No emoji, gradients, shadows or card grids.

### Code layout

```
app/src/main/java/com/dogfootprint/app/
├── App.kt                 composition root (DataStore, SDK sources, CareEngine)
├── MainActivity.kt        Compose host + debug seed intent
├── data/                  AppData / DogProfile / Trip models, DataStore repository
├── care/                  BreedPresets, Zones, ComfortRules (pure, unit-tested), CareEngine (trip + guidance brain)
├── pleos/                 VehicleSource, NaviSource, LocationSource, VoiceGuide (Pleos TTS + chime), ClimateActuator
├── audio/                 CalmingPlayer (CC0 music rotation / TTS story)
└── ui/                    NavigationRail + Home / Footprints / Profile / Settings screens
```

Before an App Market submission, remove the `CONTROL_CAR_*` permissions from the manifest unless Vehicle Control has
been approved for the project.
