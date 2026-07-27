# Workflow — 형상 관리

이 프로젝트의 버전 관리 전략. 1인 개인 프로젝트이고, 코드 대부분을 Claude Code가
쓰며, 개인 데이터 커밋이 절대 금지라는 세 제약에 맞춰 가볍게 잡았다.
근거는 `decisions.md` 26.

## 브랜치

- **짧은 브랜치에서 작업하고, 셀프 리뷰 후 `main`에 머지한다.**
- 브랜치 이름은 자유. 작업 하나에 하나, 오래 끌지 않는다.
- 머지 전 `/code-review`로 AI가 쓴 코드를 사람이 확인한다. 이게 이 프로젝트에서
  브랜치를 쓰는 유일한 이유다 — AI가 짠 것을 바로 히스토리에 넣지 않는 관문.
- `main`은 항상 빌드되는 상태로 유지한다.

## 커밋

- **논리 단위 하나에 커밋 하나.** WIP 더미 커밋을 남기지 않는다.
- 설계 결정이 걸린 변경은 커밋 메시지에 **결정 번호를 참조**한다.
  예: `수집 foreground service 골격 (decisions #17)`
- 이렇게 하면 몇 달 뒤 히스토리가 "무엇을"만이 아니라 "왜"의 근거(`decisions.md`)로
  이어진다.
- 커밋 메시지는 한국어. 명령형/요약형 어느 쪽이든 일관되게.

## 원격

- **GitHub 공개(public) 저장소.**
- 이유: `INDEX.md`가 raw URL을 새 대화에 붙여넣어 Claude가 문서를 읽는
  워크플로우를 전제로 한다. 공개라야 인증 없이 그 URL이 열린다.
- 공개해도 안전한 전제: **저장소엔 설계 문서와 코드만 들어간다. 개인 데이터·키는
  절대 커밋하지 않는다**(아래 안전망). 그래서 공개가 성립한다.

## 개인 데이터·시크릿 안전망

3중으로 막는다.

1. `.gitignore` — `/personal/`, `secrets.properties`, `local.properties`,
   `*.keystore/.jks/.p12`, `.env`, `transcripts/`, `*.diary.md`, 빌드 산출물.
2. **pre-commit 훅** (`scripts/git-hooks/pre-commit`) — `.gitignore`를 우회한
   실수(`git add -f`, 오타)를 잡는다. 민감 경로/파일, `sk-ant-` 키, PEM 개인키,
   자격증명형 할당을 스테이징에서 발견하면 커밋을 거부한다.
3. 커밋 전 `git status`로 무엇이 들어가는지 눈으로 확인.

### 훅 설치 (clone마다 1회)

`core.hooksPath`는 로컬 git 설정이라 커밋되지 않는다. 저장소를 새로 clone하면
저장소 루트에서 다시 실행해야 한다.

```bash
git config core.hooksPath scripts/git-hooks
chmod +x scripts/git-hooks/pre-commit   # Windows Git Bash 포함
```

오탐이 확실할 때만 `git commit --no-verify`로 우회한다. 습관이 되면 안전망이
아니다.

## 태그

- v1 완료 기준(`scope.md`: 2주 5일)을 만족하면 `v1` 태그를 단다.
- 그 전에는 태그를 남발하지 않는다. 개인 앱이라 릴리스 개념이 없다.

## 테스트 데이터

- 파이프라인 검증용 합성 픽스처가 필요하면 `/personal/` 아래에 둔다(커밋 안 됨).
- 실제 일기·프로필·위치·대화 로그는 픽스처로도 커밋하지 않는다(`CLAUDE.md`).
