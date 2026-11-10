# 코딩 규칙

코드 리뷰 때 보는 **판단 규칙**만 적습니다. 기계적인 규칙은 도구가 `./gradlew build`(CI 포함)에서 잡으므로 리뷰에서 다시 보지 않습니다.
- 서식: ktlint (`.editorconfig`). 고치기: `./gradlew ktlintFormat`
- 모듈 의존 방향, `:art`·`:model`의 Android·Pleos SDK 금지: 루트 `build.gradle.kts`의 `checkModuleBoundaries`
- 공개 범위 명시: `:art`·`:vehicle`은 Kotlin explicit API 모드라 `public`/`internal`을 안 적으면 컴파일 오류

## 규칙
1. **공통 값은 한 곳에서.** SDK 레벨·라이브러리 버전은 `gradle/libs.versions.toml`에만 둡니다. [docs/interfaces.md](docs/interfaces.md) 공통 상수(샘플 간격, 속도색 최대 속도 등)는 코드에서도 상수 하나로만 정의하고 가져다 씁니다. 테스트 코드도 같습니다(예: `1_000L` 대신 `SAMPLE_INTERVAL_MS`). 여러 화면이 같이 쓰는 배치 값(넓은 화면 기준 폭, 화면 여백 등)도 상수 하나로 정의합니다.
2. **드러나는 의존은 `api()`.** 모듈의 공개 함수·타입에 다른 모듈의 타입(예: `:model`의 주행 세션)이 나오면 `api()`, 안쪽에서만 쓰면 `implementation()`.
3. **용어는 GLOSSARY를 따름.** 클래스·함수 이름은 [GLOSSARY.md](GLOSSARY.md)의 "코드에서 쓰는 이름"을 씁니다. "피할 말"은 코드에도 쓰지 않습니다. GLOSSARY를 바꾸는 시점 (이 항목이 기준, #37):
   - 기존 행의 TBD 코드 이름 칸: 그 이름을 처음 쓰는 PR에서 함께 채웁니다.
   - 새 행: 뜻이 이미 스펙 이슈·[docs/interfaces.md](docs/interfaces.md)·[ADR](docs/adr/README.md)에 있는 용어면 처음 쓰는 PR에서 추가하고, PR 본문에 출처를 적습니다.
   - 어디에도 없는 새 개념, 기존 행의 뜻 변경: GLOSSARY만 바꾸는 PR을 먼저 머지합니다. 뜻이 애매하면 이슈에서 합의합니다.
4. **단위가 보이게.** 속도(km/h), 시각(epoch ms), 각도(도)처럼 단위가 있는 값은 이름이나 타입에 단위가 드러나게 합니다.
5. **그림 로직은 결정적.** `:art`는 같은 입력이면 같은 그림 데이터를 냅니다. 현재 시각·난수·기기 설정을 직접 읽지 않고 입력으로 받습니다.
6. **모듈 밖 공개는 계약에 있는 것만.** `:art`·`:vehicle`에서 `public`은 [docs/interfaces.md](docs/interfaces.md)의 출력에 있는 것에만 붙입니다. 어댑터 입력 타입, 저장 구조 같은 모듈 안 세부는 `internal`입니다. 새로 공개해야 하면 interfaces.md 합의가 먼저입니다.
7. **공유 규칙은 `:model` 것을 씀.** 하루 기록 주행 순서는 `inDriveOrder()`, 깜빡이 켜짐 판정은 `TurnSignal?.isOn`을 씁니다 ([docs/interfaces.md](docs/interfaces.md) 0번 공유 규칙, #47). 하루 그림과 맵 그래프가 같은 순서·같은 판정을 쓰게 하려는 규칙입니다. 새 공유 규칙이 필요하면 interfaces.md 합의가 먼저입니다.
8. **그리기 코드는 그리기만.** `:app`의 그리기 함수(`DrawScope` 안, 테마의 `ThemeArt` 구현)에 해당합니다 (#87 리뷰에서 테마마다 되풀이된 지적, #105).
   - 반복 연출은 매 프레임 다시 그립니다. 입력(하루 그림 데이터, 경로)에서 나오는 계산(묶기, 평균, 위치 찾기)은 입력마다 한 번만 하고(`LastComputed`), 프레임마다 객체(목록, `Brush` 등)를 새로 만들지 않습니다.
   - 크기는 dp(`* density`)나 칸 크기의 비율로 씁니다. px 숫자를 그대로 쓰지 않습니다.
   - 테마가 같이 쓰는 값과 도우미는 `ThemeArt.kt`·`DayArt.kt`·`MapGraph.kt`의 공용 것을 씁니다(예: `DRAWING_RADIUS_SCALE`, `DRAW_IN_GHOST_ALPHA`, `grownFraction`, `isThumbnail`, `forEachBackdropSpot`, `forEachTurnSignalStart`). 같은 뜻의 상수나 식을 테마 파일에 다시 만들지 않습니다.
   - 샘플 하나짜리 구간(점 하나)도 그립니다. 선만 그리면 그 구간이 사라집니다.
   - 그려지기가 끝나면(진행 1) 모든 부분이 다 그려져 있어야 합니다.
