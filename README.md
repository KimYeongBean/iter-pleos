# Iter (이테르)

> 하루의 주행을 차량 신호로 기록해, 주차 후 레이싱 트랙 같은 맵 그래프와 한 장의 그림으로 돌려주는 Pleos 주행 기록 앱

## 앱 이름 및 한 줄 소개
- **앱 이름:** Iter (이테르)
- **한 줄 소개:** Iter는 하루의 주행을 속도·방향지시등·와이퍼 같은 차량 신호로 기록해, 주차 후 레이싱 트랙 같은 맵 그래프와 한 장의 그림으로 돌려주는 Pleos(Android Automotive OS) 주행 기록 앱입니다.
- **출품:** 현대자동차 Pleos 앱 개발 공모전(대학생 부문), 팀 포코코

### 주요 기능
| 기능 | 하는 일 |
|---|---|
| 주행 기록 | 기어를 D로 놓으면 기록을 시작하고, 정차 후 P에서 60초가 지나면 끝냅니다. 앱을 열지 않아도 시동 때부터 1초마다 차속·방향지시등·와이퍼·전조등·조향각·브레이크를 기록합니다. |
| 맵 그래프 | 지도 배경 없이 그날의 경로를 레이싱 트랙처럼 선으로 그립니다. 속도는 색의 진하기로, 방향지시등 구간은 배경색으로, 와이퍼 구간은 비 효과로, 전조등 구간은 어둡게 나타냅니다. |
| 하루 그림 | 하루의 시간·속도·차량 신호로 그래프 아트 한 장을 만듭니다. 위치 없이 그립니다. |
| 날짜별 기록 | 앱의 첫 화면입니다. 날짜별 칸마다 그날의 하루 그림 축소본이 쌓이고, 칸을 누르면 그날의 맵 그래프와 하루 그림을 봅니다. |

- 기어가 P가 아니면 시스템이 앱 화면을 가립니다('Unavailable while driving'). 기록은 뒤에서 계속되고, 그림은 정차(P) 중에 봅니다.
- 처음 실행할 때 수집 안내에 동의한 뒤에만 기록합니다. 위치는 따로 동의할 때만 기록합니다.
- 기록은 서버 없이 기기 안에만 암호화해 저장합니다. 설정 > 개인정보에서 동의를 철회하면 기록을 모두 지웁니다.

## 실행 방법
Pleos Connect Emulator를 켠 뒤 release APK([APK 파일 위치](#apk-파일-위치))를 설치하고 실행합니다. `adb`가 PATH에 있어야 합니다.
```bash
adb install apk/포코코_Iter_release.apk
adb shell am start --user 10 -n io.github.kimyeongbean.iter/.app.MainActivity
```
에뮬레이터 앱 목록에서 Iter 아이콘을 눌러도 됩니다.

### 처음 실행
처음 실행하면 아래 순서로 창이 뜹니다 (Pleos Connect Emulator v2.1.0에서 확인).
1. **주행을 기록하기 전에** (수집 안내): 맵 그래프에 경로까지 보려면 **위치도 기록하기 (선택)** 스위치를 켜고 **동의하고 시작**을 누릅니다.
2. **Allow Iter to access this device's location?**: **While using the app**을 누릅니다.
3. **Allow Iter to send you notifications?** (잠시 뒤에 뜸): **Allow**를 누릅니다. 기록하는 동안 알림이 보입니다.
4. **앱을 닫아도 경로를 기록할까요?**: **나중에**를 눌러도 됩니다. 테스트에는 필요 없습니다.
5. 날짜별 기록 화면이 보이고, 오늘 카드에 '오늘은 아직 기록이 없어요'가 나옵니다. 이제 [주행 테스트](#주행-테스트)로 주행을 하나 만듭니다.

## 테스트 환경 및 필요한 설정
| 항목 | 내용 |
|---|---|
| 실행 환경 | Pleos Connect Emulator v2.1.0 이상 (Virtual Console 지원 최소 버전). 개발에 쓴 버전과 화면 규격은 [아래](#에뮬레이터-버전과-화면-규격)에 있습니다. 실차에서는 확인하지 않았습니다. |
| 필요한 도구 | `adb` (Android SDK Platform-Tools) |
| 권한 | 위치(선택)·알림을 처음 실행할 때 묻습니다. 차속 권한(`android.car.permission.CAR_SPEED`)은 이 에뮬레이터에서 창 없이 허용됩니다. |
| 실행 사용자 | AAOS 에뮬레이터는 사용자 10으로 돌아서 `am start`에 `--user 10`을 붙입니다. |
| 화면 | 기어가 P일 때만 앱 화면이 보입니다. 그 밖에는 시스템이 'Unavailable while driving'으로 가립니다(주행 중 사용 가능으로 선언하지 않음). 기록은 뒤에서 계속됩니다. |

### 주행 테스트
[처음 실행](#처음-실행)까지 마친 뒤, 아래 명령 하나로 주행을 하나 만듭니다. 약 3분 20초 걸립니다. Windows에서는 Git Bash에서 실행합니다(`adb`, `curl` 필요).
```bash
bash scripts/drive-test.sh
```
스크립트가 하는 일:
1. 에뮬레이터 위치를 서울 시청 부근으로 옮기고 기어를 **D**로 둡니다.
2. 120초 동안 40~70 km/h로 트랙 모양 길을 한 바퀴 돕니다. 도중에 왼쪽·오른쪽 방향지시등, 와이퍼, 전조등(로우빔)을 켭니다.
3. 정차해 기어를 **P**로 두고 70초 기다립니다. P에서 60초가 지나면 주행 기록이 끝납니다.

달리는 동안 앱 화면은 시스템의 'Unavailable while driving'으로 가려집니다. 정상이며, 기록은 뒤에서 계속됩니다.

**확인할 것** (스크립트가 '완료'를 출력한 뒤)
| 화면 | 보여야 하는 것 |
|---|---|
| 날짜별 기록 | 오늘 칸에 하루 그림 축소본이 들어가고, 오늘 카드에 '주행 1회 · 시작–끝 시각'과 **오늘 기록 보기** 버튼이 나옵니다. |
| 오늘 기록 보기 → 그날 화면 | 왼쪽에 하루 그림, 오른쪽에 맵 그래프가 나옵니다. 맵 그래프에는 트랙 한 바퀴 경로, 깜빡이 켠 구간(점), 와이퍼 켠 구간(비 빗금), 전조등 켠 구간(진한 선), 출발·도착 시각이 보입니다. |

**손으로 해 보기** (스크립트 없이)
1. `adb forward tcp:8088 tcp:8088`을 실행하고 PC 브라우저에서 `http://127.0.0.1:8088/`(Virtual Console)을 엽니다. Virtual Console은 이 페이지가 열려 있는 동안만 차량 값을 받으니 닫지 않습니다.
2. Gear Selection에서 **D**를 고르고 **ACCEL**을 누르고 있으면 차속이 오릅니다.
3. 정차한 뒤 **P**로 두고 60초가 지나면 날짜별 기록의 오늘 칸에 나타납니다.

손으로 하면 위치가 한 곳에 머물러 맵 그래프에 경로 선이 생기지 않습니다. 경로를 그리려면 달리는 동안 `adb emu geo fix 경도 위도`로 좌표를 바꿔 보냅니다(예: `adb emu geo fix 126.9780 37.5665`). 위치 기록에 동의하지 않았다면 맵 그래프에 '경로 정보 없음'이 나오고, 하루 그림은 그대로 그려집니다.

**참고**
- 이 에뮬레이터는 user 빌드라 `adb shell cmd car_service inject-vhal-event`로는 신호를 넣을 수 없습니다. 스크립트는 Virtual Console HTTP API(`/vehicle/gear`, `/vehicle/controls` 등)를 씁니다. 이 API는 브라우저 페이지가 열려 있을 때만 동작해서, 스크립트가 페이지 대신 1초마다 `/browser/session` 신호를 보냅니다.
- 지금 화면을 파일로 저장하려면 `adb exec-out screencap -p > screen.png`를 씁니다. Windows PowerShell 5에서는 `>`가 파일을 깨뜨리니 cmd나 Git Bash에서 실행합니다.

### 사용한 차량 신호
에뮬레이터(Virtual Console)에서 모두 받는 것을 확인했습니다.

| 신호 | 받는 곳 | Virtual Console 조작 | 결과 |
|---|---|---|---|
| 기어 | Pleos SDK `Gear` | Motion > Gear Selection | 받음 (P·D 확인). 주행 기록의 시작·끝 기준 |
| 차속 | CarPropertyManager `PERF_VEHICLE_SPEED` (권한 `android.car.permission.CAR_SPEED`) | ACCEL·BRAKE 페달 | 받음 (0~240 km/h) |
| 방향지시등 | Pleos SDK `TurnSignal` | Convenience > Turn Signal State | 받음 (좌·우·꺼짐). SDK에 비상 상태가 없어 비상등은 기록하지 않음 |
| 와이퍼 | Pleos SDK `Wiper` (앞 유리) | Convenience > Windshield Wiper Switch | 받음 |
| 전조등(로우빔) | Pleos SDK `Light` | Convenience > Exterior Lights Mode > Low beam | 받음 |
| 조향각 | Pleos SDK `SteeringWheel` | Motion > Steering Angle, 핸들 | 받음. Virtual Console에 넣은 값과 부호가 반대로 들어옴(-25 → 25) |
| 브레이크 페달 | Pleos SDK `Brake` | Motion > Brake Pedal | 받음 (밟음·안 밟음) |
| 위치 | LocationManager GPS | `adb emu geo fix` | 받음. 에뮬레이터 기본 좌표에 고정되어 있어 직접 옮겨야 경로가 움직임 |

### 소스에서 빌드
JDK 17 (CI 기준. JDK 21에서도 빌드됩니다. JDK 24 이상은 이 프로젝트의 Gradle 8.11.1이 지원하지 않습니다)과 Android SDK (compileSdk 35)가 필요합니다. 환경 변수 `ANDROID_HOME`에 SDK 경로를 두거나 `local.properties`에 `sdk.dir`을 적습니다.
```bash
./gradlew build            # 빌드 + JVM 단위 테스트
./gradlew assembleRelease  # app/build/outputs/apk/release/
```
release APK를 서명하려면 저장소 맨 위에 `keystore.properties`를 둡니다. 이 파일과 키스토어는 커밋하지 않습니다(`.gitignore`). 파일이 없으면 서명 없는 APK(`app-release-unsigned.apk`)가 나옵니다.
```properties
storeFile=키스토어 파일 경로 (절대 경로, 또는 저장소 맨 위 기준 상대 경로)
storePassword=…
keyAlias=…
keyPassword=…
```
release 빌드에는 디버그 메뉴가 없습니다. 주행 없이 화면을 바로 보고 싶으면 debug 빌드를 설치하고 디버그 메뉴의 **가짜 기록 넣기**를 씁니다.
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 에뮬레이터 버전과 화면 규격
개발과 테스트에 쓴 에뮬레이터입니다. 값은 실행 중인 에뮬레이터에서 `adb shell wm size`, `wm density`로 확인했습니다.

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (빌드 `RELEASE.release_connect_v2.1.0`) |
| 시스템 이미지 | Android 14 (API 34) Automotive, x86_64 |
| 화면 규격 | 2560 × 1440 px (가로), 160 dpi (mdpi, 1 dp = 1 px), 60 Hz |

## APK 파일 위치
`apk/포코코_Iter_release.apk` — 서명된 release APK (버전 0.1.0, 패키지 `io.github.kimyeongbean.iter`)

직접 만들 때는 위 [소스에서 빌드](#소스에서-빌드)를 봅니다.

## 프로젝트 구조
| 모듈 | 하는 일 |
|---|---|
| `:vehicle` | 차량 신호·위치 수집, 주행 기록 시작·끝 판단, 기록 저장 |
| `:app` | 화면(날짜별 기록, 그날 화면의 맵 그래프·하루 그림, 설정), 기록 서비스 |
| `:art` | 하루 그림 데이터와 속도색 계산, 가짜 주행 데이터. 안드로이드·SDK에 의존하지 않는 순수 Kotlin |
| `:model` | 모듈이 함께 쓰는 데이터 타입. 순수 Kotlin |

`:art`와 `:model`은 JVM 단위 테스트로 검증합니다.

## 오픈소스 라이선스
앱에 들어가는 라이브러리 목록은 [AboutLibraries](https://github.com/mikepenz/AboutLibraries) 플러그인으로 뽑습니다.
```bash
./gradlew :app:exportLibraries -PaboutLibraries.exportVariant=release
```
2026-11-15 기준 104개이고, Pleos Vehicle SDK 3개와 SQLCipher 1개를 뺀 100개가 모두 Apache-2.0입니다. 직접 넣은 것은 아래와 같고 나머지는 이들이 끌어오는 라이브러리입니다.

| 라이브러리 | 라이선스 |
|---|---|
| AndroidX Core·Activity·Compose(UI·Foundation·Animation·Material3)·Room·SQLite | Apache-2.0 |
| Kotlin 표준 라이브러리, kotlinx-coroutines | Apache-2.0 |
| SQLCipher for Android (`net.zetetic:sqlcipher-android`, 기록 저장소 암호화) | BSD 계열 SQLCipher Community Edition 라이선스 ([원문](https://www.zetetic.net/sqlcipher/license/)). POM에 라이선스 이름이 없어 AboutLibraries 목록에는 표기 없음으로 나옴 |
| Pleos Vehicle SDK (`ai.pleos.playground:Vehicle`) | 표기 없음 (Pleos Playground 공개 SDK) |
| Asta Sans 글꼴 | SIL OFL 1.1 ([전문](app/src/main/assets/licenses/asta_sans_ofl.txt)) |

## 협업 방식
브랜치·커밋·PR 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.
용어는 [GLOSSARY.md](GLOSSARY.md), 모듈 경계·소유자·작업 흐름은 [docs/](docs/workflow.md)를 참고하세요.
