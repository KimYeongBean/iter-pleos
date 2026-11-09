# 0001. 가짜 주행 데이터는 기록 저장소를 거쳐 넣는다

- 상태: 제안
- 날짜: 2026-11-09
- 관련 이슈: #6
- 영향받는 모듈: 차량 데이터(`:vehicle`), 그림 로직(`:art`), 화면(`:app`)
- 결정한 사람: KimYeongBean

## 맥락
에뮬에서 차속·기어·위치 수신은 11-05 기준 미확인이고, 11-13 기능 동결 전에 시연 영상과 몇 주치 잔디를 보여 줘야 합니다.
Pleos 공식 문서와 공모전 안내를 확인한 결과(2026-11-09), 경로나 속도 변화를 자동으로 재생하는 **주행 시뮬레이션 도구는 문서에 없습니다.**
- Virtual Console은 수동 조작판입니다. Motion(기어·브레이크·조향각·주행 거리), Convenience(와이퍼·조명), ADAS(크루즈 목표 속도) 등을 손으로 바꿉니다. "주행 및 차량 이벤트 시나리오 재현"이 목적으로 적혀 있지만 자동 재생·스크립트 기능 설명은 없습니다. ([Virtual Console 셋업 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator))
- 공모전 개발 환경 안내는 에뮬레이터 사용만 안내하고, 시뮬레이션 도구나 테스트 데이터는 제공하지 않습니다. ([개발 환경](https://appdevchallenge.auto-hmg.io/guide/development))
- NaviHelper 문서에 경로 안내 시뮬레이션(모의 주행) API는 보이지 않습니다. 내장 내비 앱의 모의 주행 여부는 미확인입니다. ([NaviHelper 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro))

검토한 선택지:
1. 가짜 주행 데이터를 완성된 주행 세션으로 만들어 기록 저장소에 저장한다.
2. 실시간 신호 흐름을 가짜로 바꿔 주행을 흉내 낸다.

## 결정
1번. 그림 로직이 가짜 주행 데이터(완성된 주행 세션)를 만들고, 화면의 디버그 메뉴가 이를 실제 기록과 같은 기록 저장소에 저장한다. 화면은 기록 저장소만 읽는다.
실시간 경로는 Virtual Console 수동 조작으로 실제 파이프라인을 통과시켜 검증한다(시연 영상의 실제 SDK 사용 장면).

## 결과
- 좋아지는 점: 모듈 사이 계약이 기록 저장소 하나로 줄어든다. 신호 확인 결과와 상관없이 화면·그림을 만들고 시연할 수 있다. 여러 날짜 잔디를 바로 채운다.
- 감수하는 점: 가짜 주행 데이터는 실시간 흐름(세션 시작·끝 감지)을 시험하지 않는다. 이 부분은 차량 데이터 트랙이 Virtual Console로 따로 확인한다.
- 차량 데이터 트랙이 확인할 것: Virtual Console에서 속도를 바꿀 방법(ADAS 크루즈 목표 속도가 차속에 반영되는지, adb 주입), 내장 내비 모의 주행으로 위치가 움직이는지.
- 반영: [docs/interfaces.md](../interfaces.md) 0~3번, [GLOSSARY.md](../../GLOSSARY.md) 가짜 주행 데이터·샘플·그림 데이터.
