# 웹 버전 세팅 (Firebase) — 1단계

결정 51. 구조: `[안드로이드] --동기화--> [Firestore] <--실시간--> [웹]`, 답 생성은
Cloud Function(두뇌)이 Claude 키를 들고 한다. 1단계는 **웹 단독으로 실시간 대화**까지.

## A. Firebase 콘솔에서 할 것 (사람만 할 수 있는 부분)

1. https://console.firebase.google.com 에서 **프로젝트 생성**.
2. **Firestore Database** 만들기 → 위치 **asia-northeast3 (서울)** → 테스트 모드로 시작
   (규칙은 곧 `firestore.rules`로 덮어씀).
3. **Authentication** → **로그인 방법** → **익명(Anonymous)** 사용 설정.
   (1단계 테스트용. 실제 민감 데이터 전에 Google 로그인 + uid 잠금으로 조인다.)
4. **Functions**를 쓰려면 **Blaze(종량제)** 플랜으로 업그레이드. 1인 사용은 무료
   한도 안이라 실제 과금은 거의 0.
5. **웹 앱 등록**(</> 아이콘) → `firebaseConfig` 복사 →
   `web/firebase-config.example.js`를 `web/firebase-config.js`로 복사해 값 채우기.
6. `.firebaserc`의 `YOUR_FIREBASE_PROJECT_ID`를 실제 프로젝트 ID로.

## B. Claude 키를 Function 시크릿으로

```bash
firebase functions:secrets:set ANTHROPIC_API_KEY
# 프롬프트에 secrets.properties의 그 키를 붙여넣기
```

## C. 배포

```bash
cd functions && npm install && cd ..
firebase deploy --only firestore:rules,functions,hosting
```

- 배포 후 Hosting URL(`https://<프로젝트>.web.app`)이 웹 주소.
- Functions 첫 배포 때 필요한 API(Eventarc 등) 사용 설정을 물으면 허용.

## D. 테스트

1. Hosting URL 접속 → "연결 중…"이 사라지고 입력창이 활성화되면 로그인 OK.
2. (선택) 상단 '컨텍스트'에 아무 안내나 붙여넣고 저장.
3. 메시지 전송 → 몇 초 뒤 assistant 답이 실시간으로 뜨면 파이프라인 성공
   (웹 → Firestore → Function → Claude → Firestore → 웹).

## 문제 시

- 답이 안 옴: Functions 로그 확인 `firebase functions:log`. 보통 시크릿 미설정 또는
  Firestore/Function 리전 불일치.
- 권한 오류: Auth 익명 로그인 사용 설정됐는지, 규칙이 배포됐는지 확인.

## 다음 (2단계, 별도 진행)

- 안드로이드에 "웹과 동기화" 버튼: 오늘 시스템 프롬프트(프로필·브리핑) + 오늘 대화를
  Firestore로 push, Firestore 변화를 안드로이드도 구독 → 양방향 실시간.
- 그 전에 규칙을 Google 로그인 + 본인 uid로 잠글 것(민감 데이터 올리기 때문).
