// bedside 웹 클라이언트. Firestore로 오늘 세션을 실시간 구독하고, 내 메시지를 쓴다.
// 답 생성은 Cloud Function(두뇌)이 한다 — 웹은 Claude 키를 들지 않는다.

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.13.2/firebase-app.js";
import {
  getAuth, signInAnonymously, onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/10.13.2/firebase-auth.js";
import {
  getFirestore, collection, addDoc, query, orderBy, onSnapshot, doc, setDoc, getDoc, serverTimestamp,
} from "https://www.gstatic.com/firebasejs/10.13.2/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// 오늘 날짜(로컬) = 세션 id. 안드로이드의 sessionDate와 같은 규칙(yyyy-MM-dd).
const now = new Date();
const date =
  now.getFullYear() +
  "-" + String(now.getMonth() + 1).padStart(2, "0") +
  "-" + String(now.getDate()).padStart(2, "0");
document.getElementById("date").textContent = date;

const sessionRef = doc(db, "sessions", date);
const messagesRef = collection(sessionRef, "messages");

const messagesEl = document.getElementById("messages");
const inputEl = document.getElementById("input");
const sendBtn = document.getElementById("send");
const statusEl = document.getElementById("status");
const contextEl = document.getElementById("context");

function setStatus(s) { statusEl.textContent = s; }

let lastRole = null;
function render(msgs) {
  messagesEl.innerHTML = "";
  lastRole = null;
  for (const m of msgs) {
    if (m.role !== "user" && m.role !== "assistant") continue;
    const div = document.createElement("div");
    div.className = "bubble " + m.role;
    div.textContent = m.text || "";
    messagesEl.appendChild(div);
    lastRole = m.role;
  }
  // 내 마지막 메시지 뒤 답 대기 중이면 타이핑 표시
  if (lastRole === "user") {
    const t = document.createElement("div");
    t.className = "typing";
    t.textContent = "…";
    messagesEl.appendChild(t);
  }
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

async function send() {
  const text = inputEl.value.trim();
  if (!text) return;
  inputEl.value = "";
  autoGrow();
  try {
    await addDoc(messagesRef, {
      role: "user", text, source: "web", createdAt: serverTimestamp(),
    });
  } catch (e) {
    setStatus("전송 실패: " + e.message);
  }
}

function autoGrow() {
  inputEl.style.height = "auto";
  inputEl.style.height = Math.min(inputEl.scrollHeight, 140) + "px";
}

// --- 이벤트 ---
document.getElementById("form").addEventListener("submit", (e) => { e.preventDefault(); send(); });
inputEl.addEventListener("input", autoGrow);
inputEl.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
});
document.getElementById("saveCtx").addEventListener("click", async () => {
  try {
    await setDoc(sessionRef, { context: contextEl.value, updatedAt: serverTimestamp() }, { merge: true });
    setStatus("컨텍스트 저장됨");
    setTimeout(() => setStatus(""), 1500);
  } catch (e) { setStatus("저장 실패: " + e.message); }
});

// --- 시작 ---
signInAnonymously(auth).catch((e) => setStatus("로그인 실패: " + e.message));
onAuthStateChanged(auth, async (user) => {
  if (!user) return;
  setStatus("");
  sendBtn.disabled = false;

  // 저장된 컨텍스트 있으면 채워둔다
  try {
    const s = await getDoc(sessionRef);
    if (s.exists() && s.data().context) contextEl.value = s.data().context;
  } catch (_) { /* 무시 */ }

  onSnapshot(
    query(messagesRef, orderBy("createdAt", "asc")),
    (snap) => render(snap.docs.map((d) => d.data())),
    (e) => setStatus("동기화 오류: " + e.message),
  );
});
