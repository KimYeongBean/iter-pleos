# Iter

Iter는 Pleos Vehicle SDK를 활용한 Android Automotive OS(AAOS) 앱입니다. 하루 동안의 주행 데이터(속도, 브레이크, 방향지시등, 와이퍼, 전조등, 경로)를 기록해 그래프 아트와 레이싱 스타일 맵으로 보여줍니다.

## 공모전 정보
현대자동차 Pleos 앱 개발 공모전(대학생 부문) 출품작입니다.

## 실행 방법
Pleos Connect Emulator를 켠 뒤 release APK를 설치하고 실행합니다. `adb`가 PATH에 있어야 합니다.
```bash
adb install apk/팀명_Iter_release.apk
adb shell am start --user 10 -n io.github.kimyeongbean.iter/.app.MainActivity
```
처음 실행하면 차속·위치 권한을 묻습니다. 기어가 P일 때 날짜별 기록 화면이 보이고, D로 바꾸면 '기록 중'만 보입니다. 주행을 만들어 보는 방법은 아래 [테스트 환경·설정](#테스트-환경설정)에 있습니다.

## APK 위치
`apk/팀명_Iter_release.apk` (서명된 release APK. 팀명이 정해지면 추가합니다.)

직접 만들 때는 아래 [소스에서 빌드](#소스에서-빌드)를 봅니다.

## 테스트 환경·설정
### 요구 사항
- Pleos Connect Emulator v2.1.0 이상 (Virtual Console 지원 최소 버전). 실차에서는 확인하지 않았습니다.
- 소스에서 빌드할 때: JDK 17, Android SDK (compileSdk 35). 환경 변수 `ANDROID_HOME`에 SDK 경로를 두거나 `local.properties`에 `sdk.dir`을 적습니다.
### 소스에서 빌드
```bash
./gradlew build            # 빌드 + JVM 단위 테스트
./gradlew assembleRelease  # app/build/outputs/apk/release/
```
release APK를 서명하려면 저장소 맨 위에 `keystore.properties`를 둡니다. 이 파일과 키스토어는 커밋하지 않습니다(`.gitignore`). 파일이 없으면 서명 없는 APK(`app-release-unsigned.apk`)가 나옵니다.
```properties
storeFile=키스토어 파일 경로
storePassword=…
keyAlias=…
keyPassword=…
```
release 빌드에는 디버그 메뉴(가짜 기록 넣기, 기록 초기화)가 없습니다. 디버그 메뉴가 필요하면 debug 빌드를 씁니다.
### 에뮬레이터 테스트
Pleos Connect Emulator v2.1.0에서 확인했습니다.

1. 에뮬레이터를 켜고 앱을 설치·실행합니다. 처음 실행하면 차속·위치 권한을 묻습니다.
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start --user 10 -n io.github.kimyeongbean.iter/.app.MainActivity   # 사용자 10으로 실행 (아래 참고)
   ```
   화면을 확인할 때는 지금 화면을 파일로 저장합니다. Windows PowerShell 5에서는 `>`가 파일을 깨뜨리니 cmd나 Git Bash에서 실행합니다.
   ```bash
   adb exec-out screencap -p > screen.png
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

## 오픈소스 라이선스
앱에 들어가는 라이브러리 목록은 [AboutLibraries](https://github.com/mikepenz/AboutLibraries) 플러그인으로 뽑습니다.
```bash
./gradlew :app:exportLibraries -PaboutLibraries.exportVariant=release
```
2026-11-10 기준 103개이고, Pleos Vehicle SDK 3개를 뺀 100개가 모두 Apache-2.0입니다. 직접 넣은 것은 아래와 같고 나머지는 이들이 끌어오는 라이브러리입니다.

| 라이브러리 | 라이선스 |
|---|---|
| AndroidX Core·Activity·Compose(UI·Foundation·Animation·Material3)·Room | Apache-2.0 |
| Kotlin 표준 라이브러리, kotlinx-coroutines | Apache-2.0 |
| Pleos Vehicle SDK (`ai.pleos.playground:Vehicle`) | 표기 없음 (Pleos Playground 공개 SDK) |
| Asta Sans 글꼴 | SIL OFL 1.1 ([전문](app/src/main/assets/licenses/asta_sans_ofl.txt)) |

## 협업 방식
브랜치·커밋·PR 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.
용어는 [GLOSSARY.md](GLOSSARY.md), 모듈 경계·소유자·작업 흐름은 [docs/](docs/workflow.md)를 참고하세요.
