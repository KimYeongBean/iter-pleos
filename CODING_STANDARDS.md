# 코딩 규칙

코드 리뷰 때 보는 **판단 규칙**만 적습니다. 기계적인 규칙은 도구가 `./gradlew build`(CI 포함)에서 잡으므로 리뷰에서 다시 보지 않습니다.
- 서식: ktlint (`.editorconfig`). 고치기: `./gradlew ktlintFormat`
- 모듈 의존 방향, `:art`·`:model`의 Android·Pleos SDK 금지: 루트 `build.gradle.kts`의 `checkModuleBoundaries`

## 규칙
1. **공통 값은 한 곳에서.** SDK 레벨·라이브러리 버전은 `gradle/libs.versions.toml`에만 둡니다. [docs/interfaces.md](docs/interfaces.md) 공통 상수(샘플 간격, 속도색 최대 속도 등)는 코드에서도 상수 하나로만 정의하고 가져다 씁니다. 테스트 코드도 같습니다(예: `1_000L` 대신 `SAMPLE_INTERVAL_MS`).
2. **드러나는 의존은 `api()`.** 모듈의 공개 함수·타입에 다른 모듈의 타입(예: `:model`의 주행 세션)이 나오면 `api()`, 안쪽에서만 쓰면 `implementation()`.
3. **용어는 GLOSSARY를 따름.** 클래스·함수 이름은 [GLOSSARY.md](GLOSSARY.md)의 "코드에서 쓰는 이름"을 씁니다. 칸이 TBD면 PR에서 이름을 정하고 같은 PR에서 GLOSSARY를 채웁니다. "피할 말"은 코드에도 쓰지 않습니다.
4. **단위가 보이게.** 속도(km/h), 시각(epoch ms), 각도(도)처럼 단위가 있는 값은 이름이나 타입에 단위가 드러나게 합니다.
5. **그림 로직은 결정적.** `:art`는 같은 입력이면 같은 그림 데이터를 냅니다. 현재 시각·난수·기기 설정을 직접 읽지 않고 입력으로 받습니다.
