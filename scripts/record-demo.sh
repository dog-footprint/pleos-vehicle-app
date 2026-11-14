#!/usr/bin/env bash
# Records the scripted demo on the Pleos emulator (display + audio) and writes a scene timeline.
# Prereqs: emulator `Pleos_Connect` running, debug build installed, `scripts/vehicle-sim.sh forward` done.
# Output: <outdir>/demo-<n>.webm segments and <outdir>/scenes.json  →  render with scripts/make-demo-video.sh
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
OUT=${1:-build/demo}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
V=scripts/vehicle-sim.sh
T=scripts/tap-text.sh
# Rail items have fixed positions on the Pleos_Connect AVD; tapping by coordinate is instant, while a
# uiautomator text tap costs ~4 s on this 2560x1440 screen (kept for buttons that move with content).
rail() { case $1 in 홈) adb shell input tap 893 230 ;; 발자국) adb shell input tap 892 306 ;; 프로필) adb shell input tap 892 382 ;; 설정) adb shell input tap 893 458 ;; esac; }

ODO=100.0
drive_loop() {            # keeps the odometer ticking so the app sees "moving"
  while true; do ODO=$(python3 -c "print(round($ODO + 0.2, 1))"); $V odometer "$ODO" >/dev/null || true; sleep 3; done
}

SCENES=()
SEG=0
T0=0
now() { python3 -c "import time; print(round(time.time() - $T0, 2))"; }
scene() {                 # scene <id> <caption>   (caption shown until the next scene / segment end)
  local at; at=$(now)
  SCENES+=("{\"id\":\"$1\",\"segment\":$SEG,\"start\":$at,\"caption\":\"$2\"}")
  echo "[seg $SEG, $at s] $1"
}
SEG_ENDS=()
start_segment() {         # the emulator caps one recording at 180 s
  SEG=$1
  rm -f "$OUT/demo-$SEG.webm"
  adb emu screenrecord start "$OUT/demo-$SEG.webm" >/dev/null
  T0=$(python3 -c "import time; print(time.time())")
  sleep 1
}
stop_segment() {
  SEG_ENDS+=("$(now)")
  adb emu screenrecord stop >/dev/null
  sleep 2
}

echo "== reset state"
# Cold-start the app so no engine state (trip, motion baseline, last apply result) leaks from a previous take,
# and continue the console's odometer instead of rewinding it (a decrease would look like a reset).
adb shell am force-stop com.dogfootprint.app
ODO=$(curl -s -m 5 http://127.0.0.1:8088/vehicle/state | python3 -c "import json,sys; print(round(json.load(sys.stdin)['odometer']['traveled_distance_km'] + 0.2, 1))" 2>/dev/null || echo "$ODO")
sed 's/"controlEnabled":false/"controlEnabled":true/' scripts/demo-seed.json > "$OUT/seed.json"
scripts/demo-seed.sh "$OUT/seed.json" >/dev/null
sleep 5
$V gear Park >/dev/null; $V odometer "$ODO" >/dev/null; $V brake 0 >/dev/null
$V window rl 0 >/dev/null; $V door rl 0 >/dev/null; $V seat rear_left false >/dev/null
$V hvac "power=1&ac=1&fan=5&driver_temp=27&passenger_temp=27" >/dev/null
rail 홈; sleep 4

echo "== recording segment 0 → $OUT/demo-0.webm"
start_segment 0

scene intro "반려견 맞춤 차량 케어 · dog-footprint — 견종과 자리에 맞춰 온도·휴식·안전을 챙기고, 드라이브를 발자국으로 남깁니다"
sleep 7

scene profile "프로필: 견종·나이·멀미 성향·타는 자리 → 적정 실내 온도와 휴식 간격이 자동으로 정해집니다"
rail 프로필; sleep 4
$T "새 반려견" >/dev/null; sleep 6
rail 홈; sleep 2

scene board "탑승 → 차량이 움직이면 여행이 자동으로 기록됩니다 (Vehicle SDK: 기어·주행거리)"
$T "미탑승" >/dev/null; sleep 1
$V gear Drive >/dev/null
drive_loop & DRIVE_PID=$!
sleep 5
$V hvac "power=1&ac=1&fan=5&driver_temp=27&passenger_temp=27" >/dev/null   # the emulator tends to drop HVAC power
sleep 4

scene climate "실내 온도 19.1°C — 시베리안 허스키 적정 20–22°C → 추위 안내 + Pleos TTS 음성 안내 (하네스 미착용도 감지)"
sleep 8

scene apply "자동 조절(실험 기능): Vehicle SDK로 뒷좌석 공조 설정을 21°C·바닥 송풍으로 변경"
$T "자동 조절" >/dev/null; sleep 8

scene hot "(시뮬레이션) 실내 27°C — 이중모 대형견에게 더움 → 냉방 권고 안내"
rail 설정; sleep 1; $T "온도 27°C 가정" >/dev/null; sleep 1; rail 홈; sleep 9
rail 설정; sleep 1; $T "온도 가정 해제" >/dev/null; sleep 1; rail 홈; sleep 2

scene window "뒷좌석 창문 60% 열림 → 머리 내밀기 위험 경고 (창문 위치 실시간 감지)"
$V window rl 60 >/dev/null; sleep 9
$V window rl 0 >/dev/null; sleep 2

scene brake "급제동 반복 → 스트레스 이벤트 기록, 편안함 지수 하락"
for i in 1 2 3 4; do $V brake 100 >/dev/null; sleep 0.6; $V brake 0 >/dev/null; sleep 0.6; done
sleep 8

stop_segment
echo "== recording segment 1 → $OUT/demo-1.webm"
start_segment 1

scene break "(시뮬레이션) 90분 주행 → 휴식 안내 · 경로에 휴게소가 있으면 거기서 쉬도록 제안 (NaviHelper)"
rail 설정; sleep 1; $T "휴식 시간 도달" >/dev/null; sleep 1; rail 홈; sleep 9
$T "휴식 완료" >/dev/null; sleep 3

scene calm "진정 사운드: 앱에서 합성한 저주파 노이즈 + 호흡 리듬으로 불안을 낮춥니다"
$T "저주파 노이즈로" >/dev/null; sleep 9
$T "저주파 노이즈로" >/dev/null; sleep 1

scene end "여행 종료 → 오늘 코코의 기분 체크인"
$V gear Park >/dev/null; kill $DRIVE_PID 2>/dev/null || true
$T "여행 종료" >/dev/null; sleep 3; $T "신남" >/dev/null; sleep 2; $T "저장" >/dev/null; sleep 3

scene footprints "발자국: 거리·시간·휴식·이벤트·편안함 지수·기분이 드라이브마다 쌓입니다"
rail 발자국; sleep 4
$T "2026" >/dev/null; sleep 7

scene sdk "Pleos Connect SDK — Vehicle · NaviHelper · TextToSpeech · FusedLocation 연결 상태"
rail 설정; sleep 7

scene outro "dog-footprint — 반려견과의 모든 드라이브를 더 편안하게"
sleep 4
stop_segment
kill $DRIVE_PID 2>/dev/null || true

printf '{"segment_ends":[%s],"scenes":[%s]}\n' "$(IFS=,; echo "${SEG_ENDS[*]}")" "$(IFS=,; echo "${SCENES[*]}")" > "$OUT/scenes.json"
echo "== done: $OUT/demo-*.webm, $OUT/scenes.json"
