// Firebase 콘솔 → 프로젝트 설정 → '내 앱' → 웹 앱의 SDK 구성에서 복사한 값을 넣고
// 이 파일을 firebase-config.js 로 복사하세요(실제 파일은 gitignore됨).
// 이 값들은 비밀이 아닙니다(클라이언트 식별자). 보안은 Firestore 규칙 + 인증으로 합니다.

export const firebaseConfig = {
  apiKey: "<from firebase console>",
  authDomain: "YOUR_PROJECT.firebaseapp.com",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID",
};
