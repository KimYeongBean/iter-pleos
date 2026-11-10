# Iter

Iter는 Pleos Vehicle SDK를 활용한 Android Automotive OS(AAOS) 앱입니다. 하루 동안의 주행 데이터(속도, 브레이크, 방향지시등, 와이퍼, 전조등, 경로)를 기록해 그래프 아트와 레이싱 스타일 맵으로 보여줍니다.

## 공모전 정보
현대자동차 Pleos 앱 개발 공모전(대학생 부문) 출품작입니다.

## 개발 환경
### 요구 사항
- JDK 17
- Android SDK (compileSdk 35). 환경 변수 `ANDROID_HOME`에 SDK 경로를 두거나 `local.properties`에 `sdk.dir`을 적습니다.
- 에뮬레이터 확인: Pleos Connect Emulator v2.1.0 이상, `adb`가 PATH에 있어야 합니다.
### 빌드 및 실행
준비 중
### 에뮬레이터 / 실차 테스트
Pleos Connect Emulator v2.1.0(Virtual Console 지원 최소 버전)에서 확인했습니다.

1. 에뮬레이터를 켜고 앱을 설치·실행합니다. 처음 실행하면 차속·위치 권한을 묻습니다.
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start --user 10 -n io.github.kimyeongbean.iter/.app.MainActivity   # 사용자 10으로 실행 (아래 참고)
   adb exec-out screencap -p > screen.png   # 지금 화면을 파일로 저장
   ```
2. Virtual Console을 엽니다. 에뮬레이터가 띄우는 웹 페이지입니다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   그다음 PC 브라우저에서 `http://127.0.0.1:8088/`을 엽니다.
3. Gear Selection에서 **D**를 고르고 **ACCEL**을 누르고 있으면 차속이 오릅니다. 정차한 뒤 **P**로 두고 60초가 지나면 주행 세션이 끝나 잔디에 나타납니다.

이 에뮬레이터는 user 빌드라 `adb shell cmd car_service inject-vhal-event`로 신호를 넣을 수 없습니다. 주행을 스크립트로 재현할 때는 Virtual Console HTTP API를 씁니다 (`adb forward` 후).
```bash
curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Drive"   # Park / Reverse / Neutral / Drive
curl -X PUT "http://127.0.0.1:8088/vehicle/controls?speed=60&accelerator=0&brake=0&steering=0"   # km/h, %, 도
curl http://127.0.0.1:8088/vehicle/state   # 지금 값
```
`speed`는 그 순간 값이고, 에뮬레이터가 감속을 흉내 내 초당 약 3~5 km/h씩 줄어듭니다(브레이크를 넣으면 더 빨리). 일정 속도를 유지하려면 1초마다 다시 보냅니다. Virtual Console 페이지를 열어 두면 페이지도 브레이크를 0으로 되돌리니, 스크립트로 조작할 때는 페이지를 닫습니다.

AAOS 에뮬레이터는 사용자 10으로 돌아서, 앱 DB를 볼 때는 `run-as io.github.kimyeongbean.iter --user 10`을 씁니다.

#### 신호별 수신 결과 (2026-11-10, #18)
| 신호 | 받는 곳 | Virtual Console 조작 | 결과 |
|---|---|---|---|
| 기어 | Pleos SDK `Gear` | Motion > Gear Selection | 받음 (P·D 확인) |
| 차속 | CarPropertyManager `PERF_VEHICLE_SPEED` (권한 `android.car.permission.CAR_SPEED`) | ACCEL·BRAKE 페달 | 받음 (0~240 km/h). ADAS 크루즈 목표 속도는 차속이 아니라 쓰지 않음. adb 주입(`cmd car_service inject-vhal-event`)은 user 빌드라 막혀 있음 |
| 깜빡이 | Pleos SDK `TurnSignal` | Convenience > Turn Signal State | 받음 (좌·우·꺼짐). SDK에 비상 상태가 없어 비상은 기록되지 않음 |
| 와이퍼 | Pleos SDK `Wiper` (앞 유리) | Convenience > Windshield Wiper Switch | 받음 |
| 전조등(로우빔) | Pleos SDK `Light` | Convenience > Exterior Lights Mode > Low beam | 받음 |
| 조향각 | Pleos SDK `SteeringWheel` | Motion > Steering Angle, 핸들 | 받음. Virtual Console에 넣은 값과 부호가 반대로 들어옴(-25 → 25) |
| 브레이크 페달 | Pleos SDK `Brake` | Motion > Brake Pedal | 받음 (밟음·안 밟음) |
| 위치 | LocationManager GPS | — | 받음. 다만 에뮬레이터 기본 좌표에 고정돼 움직이지 않음. 내장 내비 모의 주행으로 위치가 움직이는지는 아직 확인하지 않음 |

## 협업 방식
브랜치·커밋·PR 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.
용어는 [GLOSSARY.md](GLOSSARY.md), 모듈 경계·소유자·작업 흐름은 [docs/](docs/workflow.md)를 참고하세요.
