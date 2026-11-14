# 현대자동차그룹 차량용 앱 개발 공모전 — reference notes

Source: https://appdevchallenge.auto-hmg.io/ (checked 2026-11-10). Re-check `/notice` for changes.

## Schedule

| Date | Event |
|---|---|
| 2026-10-21 ~ **2026-11-15** | Round 1 application (email). Resubmission allowed until the deadline; the last one counts |
| 2026-11-23 | Round 1 results |
| 2026-12-03 ~ 04 | Round 2: in-person presentation. **The app must actually work by then** |
| 2026-12-18 | Final results |
| 2027-02 | Exhibition, live demo and award ceremony at an HMG event |

## Round 1 submission

- **Required:** application form `.docx` (it is also the proposal): https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx
  - Section 5 covers implementation and your SDK/API usage plan.
- **Optional, earns tiered bonus points:** demo video (mockup, prototype or screen recording is fine), GitHub repo, APK.
  - Put the APK in the repo at `apk/팀명_앱이름_release.apk`. It must be a signed, runnable **release** build. If it is too large, use GitHub Releases.
  - A private repo is fine and no reviewer account needs to be invited (notice "비공개 GitHub 저장소 제출 방법", checked 2026-11-14).
    Keep it private until the deadline (2026-11-15 23:59), write the repo URL and the final commit hash in the email,
    then make it **public by 2026-11-16 (Mon)** and keep it public until the round-1 results (2026-11-23) — otherwise
    the development-output bonus is not counted. If it cannot be made public, email a download link to a zip of the
    source and APK before the deadline. Secrets (API keys) may be left out of the repo.
  - The README must state: app name, one-line description, how to run it, test environment, and the APK location or Release link.
- Short-form video link: optional, no bonus points. It goes to a separate Short-form Challenge with a ₩500K prize.
- Email: appdevchallenge@hyundai-partners.com
  - Subject: `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`
  - Resubmission subject: `[수정제출] [차량용 앱 개발 공모전] 팀명_대표자명`, with all team members CC'd.

## Judging (round 1)

1. **Vehicle specialization**: value that only the vehicle can offer, compared with a phone app.
2. **Creativity**: an original idea.
3. **Technology use**: provided SDK/API used in the *core* features.
4. **Business viability**: clear target customer and growth potential.

Theme: new customer experiences built on vehicle data, vehicle functions and mobility context. Examples include commuting, travel, charging, parking, family trips, pets, rest and work.

## Eligibility and prizes

- University/graduate division and general division (startups included). Individuals or teams of up to 5.
- Total ₩51M across 10 teams. Grand Prize ₩10M, Excellence ₩6M, Merit ₩4M (per division), plus Idea Special Award ₩1.5M ×2.

## Platform: Pleos Connect (42dot, based on Android Automotive OS)

- Requirements: Android **API 28+**, Gradle 8.0+, Java or Kotlin.
- Emulator: Pleos Connect system image **v2.1.0**, API 34, Automotive form factor, 4 GB RAM, cold boot.
- SDKs, all from `https://nexus-playground.pleos.ai/repository/maven-releases/`:

| SDK | Coordinate | Manifest permission(s) |
|---|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | `pleos.car.permission.CAR_*` (read) / `CONTROL_CAR_*` (control), one per domain |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP` |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | `TTS_SERVICE` (also needs Client ID/Secret) |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | `STT_SERVICE` (also needs Client ID/Secret) |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` | `FUSED_LOCATION` |

Vehicle permission per domain:

| Domain | Permission |
|---|---|
| Door | `CAR_DOORS` |
| Window, Wiper | `CAR_WINDOWS` |
| HVAC | `CAR_CLIMATE` |
| DrivingMode, Brake, Odometer | `CAR_DRIVE` |
| EvBattery, Fuel | `CAR_ENERGY` |
| CarInfo, Display | `CAR_INFO` |
| Light, TurnSignal | `CAR_LIGHTS` |
| SideMirror | `CAR_MIRRORS` |
| Safety, Tire | `CAR_SAFETY` |
| Seat | `CAR_SEATS` |
| Toll | `CAR_TOLL` |

The Gear API (`getCurrentGearSelectionState` and related calls) is **deprecated** in Vehicle 2.3.2.

**Verified on the emulator, not stated in the docs:** `pleos.car.permission.FUSED_LOCATION` is `protectionLevel=dangerous`, so it must be requested at runtime. The `CAR_*`, `NAVI_*`, `TTS_SERVICE` and `STT_SERVICE` permissions are `normal` and granted at install. When a permission is missing, SDK calls throw `SecurityException` synchronously and do not call `onFailed`.

## Spike results on the emulator (2026-11-14)

Verified with the team's Pleos Playground project credentials on the Pleos Connect v2.1.0 AVD:

| Feature | Playground status | On the emulator |
|---|---|---|
| Vehicle State (HVAC current/target temp, fan, power, seat occupancy/belt, windows, doors, brake, gear, odometer, EV SoC) | Ready to Use | works; `TRIP_DRIVING_TIME` and `DrivingMode` unsupported |
| Vehicle Control (HVAC set-point / fan direction / power, window) | Not Available Yet | **works** via the SDK set APIs |
| NaviHelper | Ready to Use | connects (no route in the emulator by default) |
| FusedLocation | Not Available Yet | works (runtime permission required) |
| TextToSpeech | Not Available Yet | **registers and speaks** (`registerApp(clientId, clientSecret)`, `Mode.HYBRID`) |

So every feature in the app's "A" and "B" tiers can be demonstrated live; the Playground status only affects App Market distribution.

## Links

- Dev environment page: https://appdevchallenge.auto-hmg.io/guide/development
- Emulator setup: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- SDK docs: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
- API reference: https://document.pleos.ai/api-reference/connect-sdk-pleos
- Vehicle SDK tutorial: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk
- Official sample app: https://github.com/Pleos-Playground/pleos-sample-vehicle-app
- Pleos Playground (account, projects, SDK access requests): https://pleos.ai/playground and https://pleos.ai/playground/console/projects
- App review guide: https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability
- FAQ: https://appdevchallenge.auto-hmg.io/faq
