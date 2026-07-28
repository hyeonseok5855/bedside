# bedside

밤에 침대에서 AI와 5~10분 이야기하고, 그 대화로 그날의 일기가 만들어지는 개인용 안드로이드 앱.

기록하려는 것은 "오늘 무엇을 했는가"가 아니라 **"그때 무슨 생각을 하고 어떤 기분이었는가"**다.
위치·사진·건강 데이터는 일기의 내용이 아니라 **질문의 재료**로만 쓰인다.

## 상태

구현 시작. 수집 레이어부터 — 첫 소스로 Health Connect 수면 읽기 스켈레톤을
붙였다(`assembleDebug` 통과, 실기기 검증 전). 순서 근거는 `docs/scope.md`.

## 스택

| 영역 | 선택 |
|---|---|
| 앱 | Kotlin + Jetpack Compose (Android Studio 없이 Gradle CLI + Claude Code) |
| LLM | Claude Sonnet 5 (대화·일기 생성), Haiku 4.5 (잔작업) |
| STT / TTS | NAVER CLOVA Speech (gRPC 스트리밍) / CLOVA Voice |
| 저장 | Room + SQLCipher (로컬 전용) |
| 건강 데이터 | Health Connect |

## 문서

전체 목록은 [INDEX.md](./INDEX.md) 참고.

가장 먼저 읽을 것: [docs/concept.md](./docs/concept.md), [docs/decisions.md](./docs/decisions.md)

## 원칙

- **로컬 우선.** 데이터는 기기 안에 있다. 서버는 API 호출 시에만 쓴다.
- **개인 데이터는 이 저장소에 절대 커밋하지 않는다.** 실제 일기, 프로필, 인물 정보, 대화 로그 전부.
