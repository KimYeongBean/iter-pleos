# ADR (Architecture Decision Record)

**여러 모듈에 영향을 주는 결정만** ADR로 남깁니다. 한 모듈 안에서 끝나는 결정은 PR 설명으로 충분합니다.

## ADR이 필요한 경우
- [docs/interfaces.md](../interfaces.md)의 입출력, 데이터 모양, 패키지 위치를 바꿀 때
- 새 공유 스키마나 공유 저장소(파일·DB)를 만들 때
- 여러 모듈이 쓰는 라이브러리·SDK 사용 방식을 정할 때
- 기능 동결 범위를 정하거나 바꿀 때

## 쓰는 방법
1. [0000-template.md](0000-template.md)를 복사해 새 파일을 만듭니다.
2. 상태 `제안`으로 PR을 올리고, 영향받는 모듈 소유자와 전역관리자(@KimYeongBean)가 리뷰합니다.
3. 합의되면 상태를 `승인`으로 바꾸고 merge합니다.
4. 이미 승인된 ADR은 고치지 않습니다. 뒤집을 때는 새 ADR을 쓰고, 옛 ADR 상태를 `대체됨 (NNNN)`으로 바꿉니다.

## 파일 이름 규칙
`NNNN-짧은-영문-kebab-제목.md`
- `NNNN`: 4자리 일련번호. 기존 최대 번호 + 1 (`0000`은 템플릿 전용)
- 예: `0001-session-storage-format.md`
