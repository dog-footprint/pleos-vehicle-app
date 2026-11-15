# 2차 PT(12/3–4)까지의 구현 계획 — 기획서와 구현의 차이 메우기

기준: 접수양식(기획서) 02의 3·4·5항에서 약속한 기능 vs. 1차 제출본(`eee8705`)의 구현. 1차 제출 후 기능 동결 해제 시점부터 시작.
SDK 가능 여부는 AAR(`Vehicle 2.3.2`, `NaviHelper 3.0.5`, `SpeechToText 3.3.1`)을 `javap`으로 확인한 결과.

## 1. 차이표

| 기획서 약속 | 1차 구현 | SDK 확인 | 판단 |
|---|---|---|---|
| ② 창문 과다 개방 경고 | ✅ 30 % 경고 / 20 % 해제 | — | 완료 |
| ② "창문 잠금 권장" / 스위치 밟아 여는 상황 예방 | ❌ 잠금 상태를 읽지 않음 | `Window.getCurrentWindowLockState / registerWindowLockState / setWindowLock` ✅ | **2차 구현** |
| ② 안전벨트(하네스) 확인 | ✅ | — | 완료 |
| ③ 공조 안내 → 원터치 → 자동 | ✅ 안내 + 실험 플래그 원터치(원인별 버튼) | — | 완료 (자동 제어는 "사용자 동의 기반" 후속) |
| ③ 진정 모드: 사운드 | ✅ CC0 음악 / TTS 이야기 | — | 완료 |
| ③ 진정 모드: 실내 조명 낮춤 | ❌ | `Light.setAmbientLightBrightness / setAmbientLightBrightnessColor / setAmbientLightSwitch` ✅ (`CAR_LIGHTS` + `CONTROL_CAR_LIGHTS`) | **2차 구현** (에뮬레이터 지원 여부 먼저 확인) |
| ③ 진정 모드: 음성 명령 | ❌ STT 미사용 | `SpeechToText.initialize → registerApp → request`, `ResultListener.onUpdated(text, isFinal)` ✅ | **2차 구현** (TTS와 같은 자격 증명, 동작 확인 필요) |
| ③ 휴식 알림 → 확인 시 휴게소 경유지 추가 | ⚠️ 제안까지만(TBT 휴게소) | `NaviHelper.addWaypoint(RequestWaypointInfo)`, `requestRouteWithWaypoints` ✅ (데이터 클래스는 난독화 → 문서 참고) | **2차 구현** |
| ③ 충전 중 산책 코스 (남은 시간 · 코스 · QR) | ❌ | `EvBattery.getEvChargeSwitchState / isEvChargePortOpen / registerEvBatteryStateOfCharge` ✅ · 남은 시간 API ❌ · 보행 경로 API ❌(외부 지도 API는 SDK 범위 밖) | **2차 축소 구현**: 충전 감지 + 잔량 변화율로 남은 시간 추정 + 산책 타이머 + 현재 위치 지도 링크 QR(외부 API 호출 없음) |
| ④ 발자국 지도(경로 위 발자국) | ⚠️ 출발·도착 좌표만 | 지도 타일/마커 SDK ❌ (`NAVI_CUSTOM_MAP` 권한은 있으나 NaviHelper 3.0.5 공개 API에 마커·오버레이 없음) | **2차 축소 구현**: FusedLocation 샘플로 경로 폴리라인을 앱 안에 그림(지도 배경 없음) |
| ④ 사진 업로드 → 핀 / 사진첩 | ❌ | 휴대폰→차량 채널 없음. 에뮬레이터 확인: Android 사진 선택기(`ACTION_PICK_IMAGES`, MediaProvider 14)와 갤러리 선택기는 동작하지만 사진을 넣을 길이 없음(`adb push`·가상 USB 볼륨 모두 권한 거부, 앱이 MediaStore에 직접 넣는 디버그 시드만 가능). 실차 USB 사진 지원은 검증 불가 | **기획서에서 제거** (6항 향후 "사진 연동 검토"만 유지). 프로필 사진 → 견종 라인아트 아이콘 ✅ 1차에 포함(SVG Repo CC0 26종) |
| ④ 누적 거리·횟수 | ✅ | — | 완료 |
| 시나리오 보조: 추위 주의 → 시트 열선 | ❌ (`VehicleSource.setSeatTemperature` 래퍼만 있음) | `Seat.setHvacSeatTemperature / checkSeatTemperatureCapabilities` ✅ | **2차 구현** (TOO_COLD 조치에 열선 단계 추가) |
| (기획서에 없음) 내리기 전 확인·펫 단독, 식사 시간, 체크인, 근거 문서 | ✅ | — | 기획서 3·4·5항에 추가해 둠 |

## 2. 작업 순서와 크기

| # | 작업 | 파일 | 크기 | 비고 |
|---|---|---|---|---|
| 1 | 창문 잠금: `windowLock()` Flow + `setWindowLock`; 창문 경고 배너에 "창문 잠그기" 보조 버튼(제어 플래그), 정차 중 체크리스트에 "창문 잠금 · 해제" 줄 | `VehicleSource`, `ClimateActuator`(Change.WindowLock), `HomeScreen`, strings, 테스트(경고 문구) | 0.5일 | 콘솔에 잠금 신호가 있는지 확인(`/vehicle/catalog`) |
| 2 | 휴게소 경유지: `BREAK_DUE` + TBT 휴게소 있을 때 "휴게소 경유" 버튼 → `addWaypoint`; 성공 시 이벤트 `WAYPOINT_ADDED` | `NaviSource`, `CareEngine`, `HomeScreen`, Models(EventType) | 0.5일 | 에뮬레이터에 경로가 없으므로 PT 전에 `requestRoute`로 경로를 만들어 두는 시연 절차 필요 |
| 3 | 시트 열선: `climateAction` TOO_COLD에 `HeatTo` + 열선 1단계(capability 확인), 조치 문구 "설정 n°C로 올리기 · 시트 열선" | `ComfortRules`, `CareEngine.keepClimate`, `ClimateActuator`, 테스트 | 0.5일 | `CONTROL_CAR_SEATS` 이미 선언 |
| 4 | 진정 모드 조명: 켤 때 앰비언트 밝기 저장 → 낮춤, 끌 때 복원 (`CAR_LIGHTS`/`CONTROL_CAR_LIGHTS` 추가) | `VehicleSource`, `CalmingPlayer` 호출부(`CareEngine.toggleCalm`), 매니페스트 | 0.5일 | 에뮬레이터 미지원이면 "지원 차종에서" 문구로 표기하고 시연은 로그로 |
| 5 | 음성 명령(STT): `VoiceCommands` 구현 — 정차/주행 무관하게 "진정 모드 켜/꺼", "쉴 시간 알려줘"; 마이크 버튼(주행 중엔 버튼 1개 규칙 유지 → 진정 버튼 길게 누르기 대신 설정 토글) | `pleos/SpeechSource.kt`(새), `CareEngine`, `HomeScreen` | 1일 | Playground "준비 중"이라 동작 확인부터; 실패 시 기획서 표현을 "지원 시"로 |
| 6 | 충전 중 산책: `EvBattery` 충전 스위치/포트 Flow, 잔량 변화율로 남은 시간 추정(최근 2분), 정차+충전 중 홈에 "산책 n분" 카드 + 타이머 + QR(현재 좌표 지도 URL, zxing 로컬 생성) | `VehicleSource`, `CareEngine`(ChargingState), `ChargingHome`(새 상태 화면), QR 라이브러리 | 1.5일 | 외부 API 호출 없음; 콘솔의 `ev` 도메인으로 충전 시뮬레이션 |
| 7 | 경로 그림: 여행 중 FusedLocation 샘플(30 s 간격)을 Trip에 저장, 발자국 상세에 Canvas 폴리라인 + 출발/도착/휴식 점 | `Models(Trip.path)`, `CareEngine`, `FootprintsScreen` | 1일 | 에뮬레이터 위치는 고정이라 시연용 경로 주입 스크립트 필요(`adb emu geo fix`) |
| 7b | ~~프로필 아이콘~~ 1차 제출에 포함: 견종별 CC0 라인아트 26종이 프로필·홈·발자국·좌석 표시에 쓰임 (`ui/DogAvatar`) | — | 완료 | 사진 업로드 대체; 2차에서 견종과 다른 아이콘 선택 허용 검토 |
| 8 | 데모·문서: 녹화 스크립트에 새 장면(창문 잠금, 경유지, 충전 산책), FEATURES.html·README·CARE_RULES 갱신, 릴리스 APK | scripts, docs | 1일 | 검사 스크립트 유지 |

합계 약 7일. 우선순위: 1 → 3 → 2 → 6 → 7 → 4 → 5 (심사 임팩트 대비 위험 순).

## 3. 기획서에서 바꿀 표현 (구현과 어긋나지 않게)
- ③ "창문 잠금을 권장합니다" → 2차 구현으로 유지 가능.
- ③ "실내 조명을 차분하게 낮춥니다" → 에뮬레이터가 Light 제어를 지원하지 않으면 "지원 차종에서" 단서.
- ③ 충전 중 산책 "외부 지도 API로 보행 경로 생성" → 공지상 SDK 범위 밖일 수 있어 **"지도 링크를 QR로 전달"**로 완화(5항에는 이미 그렇게 적음).
- ④ 휴대폰 사진 업로드·사진첩 → 기획서 본문에서 제거함(v2 반영); 6항 향후 계획 5 "사진 연동 검토"만 유지.
- 4항 시나리오 5 "휴게소가 경유지로 추가" → 2차까지 구현 예정이므로 유지.

## 4. 2차 PT 시연 준비(코드 밖)
- 에뮬레이터에 경로 설정(NaviHelper `requestRoute`)이 가능한지 확인해 휴게소 제안·경유지 장면을 라이브로.
- 콘솔 충전 신호(`ev` 도메인)와 `geo fix` 경로 주입 스크립트를 `scripts/`에 추가.
- 릴리스 빌드(제어 권한 분리본)와 디버그 빌드(시연용 트리거) 두 APK 준비.
