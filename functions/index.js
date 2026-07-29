// bedside 웹 대화의 '두뇌'. sessions/{date}/messages 에 user 메시지가 생기면
// 컨텍스트 + 전체 대화를 읽어 Claude로 답을 만들어 assistant 메시지로 되쓴다.
// 웹이든(2단계) 안드로이드든 "내 메시지"만 쓰면 여기서 답이 생성돼 양쪽 실시간 반영.
//
// Claude 키는 Function 시크릿(ANTHROPIC_API_KEY)에 둔다. 웹/브라우저엔 키가 없다.

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { defineSecret } = require("firebase-functions/params");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const Anthropic = require("@anthropic-ai/sdk");

initializeApp();
const db = getFirestore();
const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");

// Firestore와 같은 리전으로. (Firestore를 asia-northeast3(서울)로 만들었다고 가정)
setGlobalOptions({ region: "asia-northeast3" });

// 컨텍스트(안드로이드가 올린 시스템 프롬프트)가 아직 없을 때의 최소 폴백.
const FALLBACK_SYSTEM =
  '너는 "bedside"의 인터뷰어다. 라이징의 하루를 따뜻하고 편하게 듣고, ' +
  "지금 무슨 생각·기분인지 본인 입으로 말하게 한다. 존댓말로, 이모지 없이, " +
  "한 번에 하나씩 자연스럽게 묻는다.";

exports.generateReply = onDocumentCreated(
  { document: "sessions/{date}/messages/{msgId}", secrets: [ANTHROPIC_API_KEY] },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const msg = snap.data();
    if (!msg || msg.role !== "user") return; // 사용자 메시지에만 반응(assistant엔 무반응 → 루프 방지)

    const date = event.params.date;
    const sessionRef = db.doc(`sessions/${date}`);
    const messagesRef = sessionRef.collection("messages");

    // 컨텍스트 + 전체 대화(시간순)
    const sessionSnap = await sessionRef.get();
    const context =
      (sessionSnap.exists && sessionSnap.data().context) || FALLBACK_SYSTEM;

    const allSnap = await messagesRef.orderBy("createdAt", "asc").get();
    const convo = [];
    allSnap.forEach((d) => {
      const m = d.data();
      if (m.role !== "user" && m.role !== "assistant") return;
      const last = convo[convo.length - 1];
      // API 교대 규칙: 연속 같은 역할은 합친다.
      if (last && last.role === m.role) last.content += "\n\n" + (m.text || "");
      else convo.push({ role: m.role, content: m.text || "" });
    });
    if (convo.length === 0 || convo[0].role !== "user") return; // 첫 메시지는 user여야 함

    const key = ANTHROPIC_API_KEY.value();
    const anthropic = new Anthropic({ apiKey: key });
    let text;
    try {
      const resp = await anthropic.messages.create({
        model: "claude-sonnet-5",
        max_tokens: 1024,
        system: context,
        messages: convo,
        thinking: { type: "disabled" },
      });
      text = (resp.content || [])
        .filter((b) => b.type === "text")
        .map((b) => b.text)
        .join("")
        .trim();
    } catch (e) {
      console.error("Claude 호출 실패:", e);
      text = "지금 답을 못 만들었어요. 잠시 뒤 다시 시도해 주세요.";
    }

    await messagesRef.add({
      role: "assistant",
      text,
      source: "cloud",
      createdAt: FieldValue.serverTimestamp(),
    });
    await sessionRef.set({ updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  },
);
