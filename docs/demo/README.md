# Demo recording and live-demo notes

## Files

- `dog-footprint-demo.mp4` — the scripted demo (emulator capture + caption strip), made by
  `scripts/record-demo.sh build/demo` then `scripts/make-demo-video.sh build/demo docs/demo/dog-footprint-demo.mp4`.
- `scripts/check-demo.py` — run by `make-demo-video.sh` after rendering: one row per voice line (sound ≥ −35 dB within
  ±2 s?) and the count of frames where the Settings tab was selected. The render exits non-zero when either fails.
- `scripts/record-demo.sh build/demo --from-scene N` — re-records from scene N (state is rebuilt off camera), so an
  emulator crash does not mean starting over. `--list` prints the scene numbers.

## Why the voice in the video is synthesized

On Pleos the TTS service plays with `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, which the car audio policy sends to
`bus1_navigation_out`; the app's music goes to `bus0_media_out`. The emulator's `adb emu screenrecord` only captures
bus0, so every voice line was silent in earlier takes (measured: −85 dB at the logged line time).

Tried in order (3rd review G1):
1. Host-side recording of the navigation bus — needs a loopback audio device (BlackHole etc.); none on this Mac.
2. Recording mode (`record_on`, debug-only): `PleosTtsVoiceGuide` re-registers with `setAudioAttributes(USAGE_MEDIA)`.
   `dumpsys audio` then shows the TTS track as `USAGE_MEDIA` on the same device as the music, but the capture still
   contained no speech, so this path is kept only as a switch and is not used by the script.
3. **Used:** `make-demo-video.sh --voice synth` renders every line logged during the take (`tts_segment_events.json`,
   from `logcat doSpeak(...)` mapped onto the segment clocks) with macOS `say -v Yuna` and mixes it at the logged
   time; the captions of those scenes carry "(음성 재현)". `scripts/check-demo.py` then verifies ≥ −35 dB within
   ±2 s of every line and that no frame shows the Settings tab; a failure makes the render exit non-zero.

## Mix levels

`make-demo-video.sh` uses fixed gains, not `loudnorm` (single-pass loudnorm adapts over time and pumped the quiet
background up to full level): capture +6 dB (the app's calm music then peaks around −5 dB), synthesized
voice as rendered (peaks ≈ −3 dB), background bed (`calm_aria.m4a`, CC0) at −20 dB from the first frame, gated off
while the app's own music plays and side-chain ducked under every voice line, then a limiter at 0.9.

## Live PT: does the emulator's navigation bus reach the host speakers?

Do this once on the presentation laptop before the session:

1. Boot the AVD, open dog-footprint, and seed the demo profile: `scripts/demo-seed.sh`.
2. Turn the host volume up and trigger one line without touching the screen:
   `adb shell am broadcast --user 10 -a com.dogfootprint.app.DEMO -n com.dogfootprint.app/.DemoReceiver --es action cold`
   You should hear "코코에게 추워요. 19도로 올려 주세요." from the host speakers within two seconds.
3. Confirm the line was played on the navigation bus (not just logged):
   `adb shell dumpsys audio | grep -E "state:started.*USAGE_ASSISTANCE_NAVIGATION_GUIDANCE"` while it speaks.
   `adb logcat -d | grep "doSpeak(com.dogfootprint"` shows the text and the time.
4. If nothing is audible but step 3 shows the player, the host audio backend is not mixing bus1: start the
   emulator with `-audio coreaudio` (macOS) or send `record_on` for the session so the voice goes out as media.
5. `adb shell am broadcast ... --es action clear` resets the scenario.

## Scene triggers used by the script

| What | How |
|---|---|
| Cabin 16 °C / 27 °C / climate off / reset | `--es action cold` · `hot` · `hvac_off` · `clear` |
| Harness buckled / not buckled | `belt_on` · `belt_off` · `belt_clear` (emulator has no belt control; the override is debug-only) |
| Break due, hard braking | `break` · `brake` |
| Driver's door, gear, window, odometer | `scripts/vehicle-sim.sh door fl 1|0` · `gear Park|Drive` · `window rl 60` · `odometer <km>` (real vehicle signals) |
| Voice routed as media (recording only) | `record_on` / `record_off` |
