# CLAUDE.md

Claude Code가 이 저장소에서 작업할 때 따라야 할 규칙.

## 프로젝트

`bedside`는 개인용 안드로이드 일기 앱이다. 사용자가 밤에 침대에서 AI와 5~10분
음성으로 대화하면, 그 대화를 재료로 1인칭 마크다운 일기가 생성된다.
배포 계획 없음. 사용자는 한 명이다.

핵심 목적은 "그때 무슨 생각이었는지"를 남기는 것이다. 라이프로깅 데이터
(위치·사진·건강)는 **일기에 기록되는 대상이 아니라 질문을 만들기 위한 재료**다.
이 구분을 어기는 구현은 잘못된 구현이다.

## 스택

- Kotlin + Jetpack Compose
- Room + SQLCipher
- Health Connect, MediaStore, FusedLocationProvider + Geofencing
- NAVER CLOVA Speech (STT, gRPC 스트리밍) / CLOVA Voice (TTS)
- Anthropic API — Claude Sonnet 5

Android Studio를 쓰지 않는다. Gradle CLI와 adb로 작업한다.

## 빌드

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s bedside
```

## 절대 하지 말 것

- 개인 데이터를 커밋하지 않는다. 실제 일기, 프로필, 인물 정보, 대화 로그,
  실제 위치 기록. 테스트 픽스처로도 안 된다. 테스트 데이터는 `/personal/`
  아래에 두고 커밋하지 않는다.
- API 키를 소스에 하드코딩하지 않는다. `secrets.properties`를 쓴다.
- 사용자에게 보여줄 일기 본문에 센서 데이터를 나열하지 않는다.
  ("07:52 출근, 8,412보" 같은 줄이 일기에 들어가면 안 된다.)
- 라이브러리를 임의로 추가하지 않는다. 추가가 필요하면 먼저 묻는다.

## 규칙

- STT/TTS는 인터페이스 뒤에 둔다. 벤더 SDK를 대화 로직에 직접 물리지 않는다.
  교체 가능성이 높다.
- 대화의 각 턴은 즉시 로컬에 커밋한다. 사용자가 대화 중 잠들 수 있다.
- 프롬프트는 코드에 인라인하지 않는다. `docs/prompts/` 아래 마크다운을
  단일 출처로 삼는다.
- 설계 결정을 내리거나 바꾸면 `docs/decisions.md`에 이유와 함께 추가한다.
