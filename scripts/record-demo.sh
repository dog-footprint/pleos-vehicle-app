#!/usr/bin/env bash
# Records the scripted demo on the Pleos emulator (display + bus0 audio) scene by scene and writes the scene
# timeline, the segment clock and the voice lines spoken during the take (for the voice mix and the checks).
#
#   scripts/record-demo.sh build/demo                 # whole take (clears OUT)
#   scripts/record-demo.sh build/demo --from-scene 9  # keep earlier segments, rebuild the state, continue from 9
#   scripts/record-demo.sh build/demo --from-scene 1 --to-scene 5   # re-take one segment's scenes (same segment numbers)
#   scripts/record-demo.sh --list                     # scene numbers
#
# Prereqs: emulator `Pleos_Connect` running, debug build installed (DemoReceiver, seed intent). Everything
# off-screen goes through `adb broadcast` or `vehicle-sim.sh`; the Settings tab is never opened on camera.
# The emulator caps one recording at 180 s, so scenes are grouped into segments; the 30 s pet-alone grace is
# waited for between two segments and so cut from the video ("(30초 후)" in the caption).
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

V=scripts/vehicle-sim.sh; T=scripts/tap-text.sh
PKG=com.dogfootprint.app
FROM=1; TO=14; OUT=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --from-scene) FROM=$2; shift 2 ;;
    --to-scene) TO=$2; shift 2 ;;
    --list) echo "1 intro  2 profile  3 board  4 harness  5 depart  6 heat  7 window  8 break  9 arrive  10 preexit  11 alone  12 return  13 footprints  14 outro"; exit 0 ;;
    *) OUT=$1; shift ;;
  esac
done
[[ -n $OUT ]] || { echo "usage: $0 OUT_DIR [--from-scene N]"; exit 2; }
mkdir -p "$OUT"
RUN_START=$(python3 -c "import time; print(time.time())")

# ---------------------------------------------------------------- helpers
now() { python3 -c "import time; print(time.time())"; }
demo() { adb shell am broadcast --user 10 -a $PKG.DEMO -n $PKG/.DemoReceiver --es action "$1" >/dev/null; }
tap() {                   # tap <text> [x y]: uiautomator dumps take ~4 s and sometimes miss; retry, then fall back to coordinates
  local i
  for i in 1 2 3; do "$T" "$1" >/dev/null 2>&1 && return 0; sleep 1.5; done
  if [[ $# -ge 3 ]]; then adb shell input tap "$2" "$3"; echo "~~ tap '$1' by coordinates $2,$3"; return 0; fi
  echo "!! tap '$1' not found"
}
# Vertical GNB: 140 wide at x=853, buttons 124 high from y=96+24 with 8 px gaps → centres 182/314/446 (Settings 578 is never tapped).
rail() { case $1 in 홈|지금) adb shell input tap 923 182 ;; 발자국) adb shell input tap 923 314 ;; 프로필) adb shell input tap 923 446 ;; esac; }
ODO=0
drive_loop() {            # ≈60 km/h: 0.05 km every 3 s
  while true; do ODO=$(python3 -c "print(round($ODO + 0.05, 2))"); $V odometer "$ODO" >/dev/null || true; sleep 3; done
}
DRIVE_PID=""
# The console ignores an odometer that goes backwards, so every drive continues from the vehicle's current value.
drive_start() {
  ODO=$(curl -s http://127.0.0.1:8088/vehicle/odometer | python3 -c "import sys,json; print(round(json.load(sys.stdin).get('traveled_distance_km', 0), 2))" 2>/dev/null || echo 0)
  $V gear Drive >/dev/null; drive_loop >/dev/null 2>&1 & DRIVE_PID=$!
}
drive_stop() { [[ -n $DRIVE_PID ]] && kill "$DRIVE_PID" 2>/dev/null; DRIVE_PID=""; $V gear Park >/dev/null; }

SEG=-1; RECORDING=0; SEG_START=0
SCENES_FILE="$OUT/scenes.json"; SEGS_FILE="$OUT/segments.json"
# Re-take: drop the segments that held scenes FROM..TO (files, clocks, captions, voice lines) and reuse their numbers.
if [[ ( $FROM -gt 1 || $TO -lt 14 ) && -f $SCENES_FILE ]]; then
  SEG=$(python3 - "$OUT" "$FROM" "$TO" <<'PY'
import json, sys, os, glob
out, lo, hi = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
ids = ["intro","profile","board","harness","depart","heat","window","break","arrive","preexit","alone","return","footprints","outro"]
sc = json.load(open(f"{out}/scenes.json")); segs = json.load(open(f"{out}/segments.json")) if os.path.exists(f"{out}/segments.json") else []
# Segment numbers are not in scene order after re-takes, so drop by the scenes a segment holds.
first = {}
for s in sc["scenes"]:
    if s["id"] in ids: first[s["segment"]] = min(first.get(s["segment"], 99), ids.index(s["id"]))
drop = {g for g, i in first.items() if i >= lo - 1 and (hi >= 14 or i <= hi - 1)}
for g in drop:
    f = f"{out}/demo-{g}.webm"; os.path.exists(f) and os.remove(f)
sc["scenes"] = [s for s in sc["scenes"] if s["segment"] not in drop]; json.dump(sc, open(f"{out}/scenes.json", "w"), ensure_ascii=False)
json.dump([s for s in segs if s["segment"] not in drop], open(f"{out}/segments.json", "w"))
ev = f"{out}/tts_segment_events.json"
if os.path.exists(ev): json.dump([e for e in json.load(open(ev)) if e["segment"] not in drop], open(ev, "w"), ensure_ascii=False)
print(max([s["segment"] for s in segs if s["segment"] not in drop], default=-1))   # new segments get fresh numbers
PY
)
fi
start_segment() {
  SEG=$((SEG + 1)); rm -f "$OUT/demo-$SEG.webm"
  adb emu screenrecord start "$OUT/demo-$SEG.webm" >/dev/null
  SEG_START=$(now); RECORDING=1; sleep 1
  echo "== segment $SEG → $OUT/demo-$SEG.webm"
}
stop_segment() {
  [[ $RECORDING = 1 ]] || return 0
  local end; end=$(now)
  adb emu screenrecord stop >/dev/null; RECORDING=0; sleep 2
  python3 - "$SEGS_FILE" "$SEG" "$SEG_START" "$end" <<'PY'
import json, sys, os
p, seg, a, b = sys.argv[1], int(sys.argv[2]), float(sys.argv[3]), float(sys.argv[4])
d = json.load(open(p)) if os.path.exists(p) else []
d = [s for s in d if s["segment"] != seg] + [{"segment": seg, "host_start": a, "host_end": b}]
json.dump(sorted(d, key=lambda s: s["segment"]), open(p, "w"))
PY
}
scene() {                 # scene <n> <id> <caption>: caption shown until the next scene / segment end
  [[ $RECORDING = 1 ]] || start_segment
  local t; t=$(python3 -c "print(round($(now) - $SEG_START, 2))")
  python3 - "$SCENES_FILE" "$2" "$SEG" "$t" "$3" <<'PY'
import json, sys, os
p, sid, seg, t, cap = sys.argv[1], sys.argv[2], int(sys.argv[3]), float(sys.argv[4]), sys.argv[5]
d = json.load(open(p)) if os.path.exists(p) else {"scenes": []}
d["scenes"] = [s for s in d.get("scenes", []) if s["id"] != sid] + [{"id": sid, "segment": seg, "start": t, "caption": cap}]
json.dump(d, open(p, "w"), ensure_ascii=False)
PY
  echo "[seg $SEG, $t s] $1 $2"
}
mark() {                  # mark <name>: a moment on the segment clock (calm music on/off) for the checks
  local t; t=$(python3 -c "print(round($(now) - $SEG_START, 2))")
  python3 - "$OUT/events.json" "$1" "$SEG" "$t" <<'PY'
import json, sys, os
p, name, seg, t = sys.argv[1], sys.argv[2], int(sys.argv[3]), float(sys.argv[4])
d = json.load(open(p)) if os.path.exists(p) else []
d = [m for m in d if m["name"] != name] + [{"name": name, "segment": seg, "t_in_segment": t}]
json.dump(d, open(p, "w"))
PY
}
finish() {                # always (exit, failure, Ctrl-C): stop recording, drive loop, scenario overrides, media-usage switch
  stop_segment; drive_stop
  demo record_off; demo clear; demo belt_clear
  $V door fl 0 >/dev/null 2>&1 || true; $V window rl 0 >/dev/null 2>&1 || true
  [[ -n ${PREV_TZ:-} ]] && adb shell cmd alarm set-timezone "$PREV_TZ" >/dev/null 2>&1
  # Voice lines of this run on the segment clocks (the renderer mixes a synthesized voice at these times).
  adb logcat -d -v epoch | grep -E "doSpeak\($PKG\)" | sed -E 's/^ *([0-9.]+).*text=(.*), isFinal.*/\1\t\2/' > "$OUT/tts_raw.tsv" || true
  python3 - "$OUT" "$DEV_OFFSET" "$RUN_START" <<'PY'
import json, sys, os
out, off, run_start = sys.argv[1], float(sys.argv[2]), float(sys.argv[3])
segs = json.load(open(f"{out}/segments.json")) if os.path.exists(f"{out}/segments.json") else []
this_run = {s["segment"] for s in segs if s["host_start"] >= run_start - 1}
events = []
for line in open(f"{out}/tts_raw.tsv", encoding="utf-8"):
    if "\t" not in line: continue
    t_dev, text = line.rstrip("\n").split("\t", 1)
    t_host = float(t_dev) - off
    for s in segs:
        if s["segment"] in this_run and s["host_start"] <= t_host <= s["host_end"]:
            events.append({"segment": s["segment"], "t_in_segment": round(t_host - s["host_start"], 2), "text": text})
path = f"{out}/tts_segment_events.json"
old = [e for e in (json.load(open(path)) if os.path.exists(path) else []) if e["segment"] not in this_run]
json.dump(sorted(old + events, key=lambda e: (e["segment"], e["t_in_segment"])), open(path, "w"), ensure_ascii=False)
print(f"voice lines in this run: {len(events)}")
PY
  echo "== done: $OUT/demo-*.webm, $SCENES_FILE, $SEGS_FILE, $OUT/tts_segment_events.json"
}
trap finish EXIT

# ---------------------------------------------------------------- state builders (not recorded)
seed() {
  python3 scripts/demo-seed.py "$OUT/seed.json" --control "--utc-offset=${CLOCK_OFF:-9}" >/dev/null
  adb shell pm grant --user 10 $PKG pleos.car.permission.FUSED_LOCATION 2>/dev/null || true
  adb shell cmd car_service day-night-mode night >/dev/null 2>&1; adb shell cmd uimode night yes >/dev/null   # the car policy flips day/night on its own otherwise
  adb shell am force-stop $PKG; sleep 1
  scripts/demo-seed.sh "$OUT/seed.json" >/dev/null; sleep 6
  $V forward >/dev/null 2>&1; $V gear Park >/dev/null; $V door fl 0 >/dev/null; $V window rl 0 >/dev/null
  $V seat rear_left false >/dev/null   # dog present (the emulator reports this seat inverted)
  $V seat passenger true >/dev/null    # passenger seat empty (inverted as well) — otherwise the dog never counts as alone
  demo clear; demo belt_off; sleep 2
  rail 홈; sleep 1
}
prepare() {               # bring the app to the state scene N expects, without recording
  local n=$1
  seed
  if [[ $n -ge 4 ]]; then tap "태우기"; sleep 3; tap "1시간 전"; sleep 2; fi
  if [[ $n -ge 5 ]]; then demo belt_on; sleep 1; fi
  if [[ $n -ge 6 && $n -le 8 ]]; then drive_start; sleep 8; fi
  if [[ $n -ge 9 ]]; then drive_start; sleep 8; demo hot; sleep 1; drive_stop; sleep 3; fi
  if [[ $n -ge 10 ]]; then $V door fl 1 >/dev/null; sleep 4; fi
  if [[ $n -ge 11 ]]; then demo clear; $V door fl 0 >/dev/null; [[ $n -ge 12 ]] && sleep 36; fi
  if [[ $n -ge 12 ]]; then $V door fl 1 >/dev/null; sleep 4; fi
  if [[ $n -ge 13 ]]; then tap "내리기"; sleep 3; tap "좋았어요"; sleep 1; tap "완료"; sleep 2; fi
}

# ---------------------------------------------------------------- scenes (captions describe only what is on screen)
s1()  { scene 1 intro "차 안의 반려견 — 바깥 21°C면 10분 만에 실내 32°C, 열사병은 7마리 중 1마리가 목숨을 잃습니다"; sleep 5; }
s2()  { scene 2 profile "프로필: 견종을 바꾸면 적정 온도가 바뀝니다 (허스키 18–22°C → 퍼그 20–21°C)"
        # Coordinate taps (editor layout is fixed): row, 변경, size group, breed — under 10 s
        rail 프로필; sleep 1.2; adb shell input tap 1330 390; sleep 1.2; adb shell input tap 1920 509; sleep 0.8
        adb shell input tap 1841 509; sleep 0.8; adb shell input tap 2115 585; sleep 2.2
        adb shell input tap 1920 509; sleep 0.8; adb shell input tap 2065 509; sleep 0.8; adb shell input tap 2209 585; sleep 0.8; rail 홈; sleep 0.5; }
s3()  { scene 3 board "코코 태우기 → 마지막 식사 시간 (1시간 전) · 탑승 직후: 출발 전에 하네스를 채워 주세요"
        tap "태우기" 1330 1230; sleep 3; tap "1시간 전" 1532 699; sleep 7; }
s4()  { scene 4 harness "하네스 착용 확인 → 주의가 사라집니다"; demo belt_on; sleep 5; }
s5()  { scene 5 depart "출발 — 주행 중엔 온도 한 숫자와 다음 휴식 시간만 · 진정 사운드 재생 중 → 정지"
        drive_start; sleep 8; tap "진정 사운드"; mark calm_on; sleep 8; tap "정지"; mark calm_off; sleep 2; stop_segment; }
s6()  { scene 6 heat "(시뮬레이션 27°C) 열사병 위험 → 자동 조절 (차량 제어 실험 기능)"
        demo hot; sleep 7; tap "자동 조절"; sleep 7; demo clear; sleep 2; }
s7()  { scene 7 window "뒷좌석 창문 60% 열림 → 창문을 올려 주세요"; $V window rl 60 >/dev/null; sleep 7; $V window rl 0 >/dev/null; sleep 2; }
s8()  { scene 8 break "(90분 경과 가정) 쉴 시간 → 휴식 완료"; demo break; sleep 7; tap "휴식 완료"; sleep 3; }
s9()  { scene 9 arrive "도착 · P — 실내가 더우면(시뮬레이션 27°C) 내리기 전에 공조를 켜 두라고 미리 알립니다"
        demo hot; sleep 2; drive_stop; sleep 8; stop_segment; }
s10() { scene 10 preexit "운전석 문 열림 — 코코를 두고 내리시나요? 설정 20°C로 낮추기 (차량 제어 실험 기능)"
        $V door fl 1 >/dev/null; sleep 6; tap "낮추기" 1330 1230; sleep 4; demo clear; sleep 5
        $V door fl 0 >/dev/null; sleep 2; stop_segment; }          # the door closing ends this segment
s11() { sleep 36                                                    # 30 s grace, cut from the video
        scene 11 alone "문 닫힘 (30초 후) — 코코 혼자 있어요: 혼자 있은 시간과 최고 온도를 기록"; sleep 6; }
s12() { scene 12 return "운전자 복귀 — 기록 확인 → 내리기 → 오늘 코코는 어땠나요? 한 번 탭"
        $V door fl 1 >/dev/null; sleep 5; tap "내리기" 1104 1186; sleep 4; tap "좋았어요" 1155 769; sleep 3; tap "완료" 1092 1210; sleep 2; }
s13() { scene 13 footprints "발자국: 최근 2주 기록 — 속초 63 km 상세: 이벤트와 편안함 지수 이유"
        rail 발자국; sleep 3; tap "속초"; sleep 7; }
s14() { scene 14 outro "dog-footprint — 반려견 자리의 온도 한 숫자, 판정 한 단어"; sleep 3; }

# ---------------------------------------------------------------- run
# Status-bar clock near 14:00: pick the UTC offset that makes it so (no root needed for the time zone), restored in finish().
PREV_TZ=$(adb shell getprop persist.sys.timezone | tr -d '\r')
CLOCK_OFF=$(python3 -c "import time; t=time.gmtime(); o=int(round(14 - (t.tm_hour + t.tm_min/60))); print(max(-12, min(14, o)))")
if [[ $CLOCK_OFF -ge 0 ]]; then TZ_NAME="Etc/GMT-$CLOCK_OFF"; else TZ_NAME="Etc/GMT+$((-CLOCK_OFF))"; fi
adb shell cmd alarm set-timezone "$TZ_NAME" >/dev/null 2>&1; echo "clock zone $TZ_NAME (utc offset $CLOCK_OFF h) → device $(adb shell date +%H:%M | tr -d '\r')"
adb logcat -c
DEV_OFFSET=$(python3 -c "import subprocess,time; d=float(subprocess.check_output(['adb','shell','date','+%s.%N']).decode().strip()); print(round(d - time.time(), 3))")
echo "device clock offset: ${DEV_OFFSET}s"
if [[ $FROM -le 1 && $TO -ge 14 ]]; then rm -f "$OUT"/demo-*.webm "$SCENES_FILE" "$SEGS_FILE" "$OUT/tts_segment_events.json" "$OUT/events.json"; seed; else prepare "$FROM"; fi
for n in $(seq "$FROM" "$TO"); do "s$n"; done
