# AGENTS.md — AI 작업 규칙

이 파일은 Claude Code, Codex, Cursor, Gemini 등 **모든 AI 코딩 도구가 따라야 하는 기준**입니다.
사람 팀원도 AI에게 일을 시키기 전에 한 번 읽어주세요. 일반 협업 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 따릅니다.

## 사람용 3줄 요약
1. 할 일은 먼저 **이슈**로 만든다 (번호가 생김, 예: #12).
2. 이슈마다 **브랜치**를 따서 작업하고 **PR**을 올린다 (`Closes #12`).
3. 팀원 1명이 **승인**하면 merge한다. `main`에 직접 push는 막혀 있다.

## AI가 지킬 규칙
1. **`main`에 절대 직접 push하지 않는다.**
2. **이슈에서 시작한다.** 이슈 번호를 모르면 추측하지 말고 사람에게 물어본다.
3. **이슈 1개 = 브랜치 1개.** 이름 예: `feat/12-speed-graph`, `fix/15-map-crash`, `docs/…`, `chore/…`
4. **커밋 메시지에 이슈 번호를 넣는다.** 접두어는 CONTRIBUTING.md를 따른다. 예: `feat: 주행 속도 그래프 추가 (#12)`
5. **작고 집중된 변경만 한다.** 이슈와 상관없는 리팩터링·포맷 변경·파일 이동은 하지 않는다.
6. **PR은 템플릿을 채워서 올리고** 본문에 `Closes #12`를 적는다.
7. **merge, force push, 저장소 설정 변경은 하지 않는다.** merge는 사람이 승인 후에 한다.
8. **다음 파일은 사람이 명시적으로 요청할 때만 수정한다:** `AGENTS.md`, `CLAUDE.md`, `.github/` 폴더, `CODEOWNERS`. (저장소 관리자 승인이 필요한 파일)
9. **비밀정보를 넣지 않는다.** API 키, 키스토어, `local.properties`, 개인 PC 경로 등은 코드·커밋에 금지.
10. **PR 전에 빌드·테스트한다.**
    - 빌드·테스트: `./gradlew build` (JDK 17, Android SDK 필요. JVM 단위 테스트 포함. CI도 같은 명령)
11. **확실하지 않으면 추측하지 말고 사람에게 물어본다.**

## 팀 공유 문서
작업 전에 아래 문서를 확인한다. 위 규칙 1~11은 그대로 우선한다.
- [GLOSSARY.md](GLOSSARY.md) — 공유 용어
- [docs/interfaces.md](docs/interfaces.md) — 모듈 사이 입출력 (바뀌면 코딩을 멈추고 먼저 합의)
- [docs/ownership.md](docs/ownership.md) — 전역관리자·모듈 소유자
- [docs/workflow.md](docs/workflow.md) — 이슈 처리 순서, 스킬 사용, 완료 기준
- [docs/adr/](docs/adr/README.md) — 여러 모듈에 영향을 주는 결정 기록

다시 확인:
- 새 공유 용어나 모듈 경계는 위 문서에 먼저 적고 합의하기 전에는 코드·이슈에 쓰지 않는다.
- 다른 소유자의 모듈 파일은 수정하지 않는다. 필요하면 이슈로 요청한다.
