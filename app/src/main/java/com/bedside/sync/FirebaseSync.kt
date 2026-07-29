package com.bedside.sync

import android.util.Log
import com.bedside.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 안드로이드 ↔ 웹 자동 동기화(결정 52). 폰은 지금처럼 로컬에서 답을 만들고, 각 턴과
 * 컨텍스트를 Firestore에 자동 미러한다. 웹에서 온 메시지(source web/cloud)는 구독으로
 * 받아 대화에 주입한다. 로그인 실패해도 앱은 로컬로 그대로 동작한다.
 */
object FirebaseSync {

    data class Remote(val role: String, val text: String, val source: String)

    private val db get() = FirebaseFirestore.getInstance()

    /** 이메일/비번(BuildConfig)으로 조용히 로그인. 성공 여부 반환. */
    suspend fun ensureSignedIn(): Boolean = suspendCancellableCoroutine { cont ->
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        val email = BuildConfig.BEDSIDE_AUTH_EMAIL
        val pw = BuildConfig.BEDSIDE_AUTH_PASSWORD
        if (email.isBlank() || pw.isBlank()) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        auth.signInWithEmailAndPassword(email, pw)
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener {
                Log.w("bedside", "Firebase 로그인 실패: ${it.message}")
                cont.resume(false)
            }
    }

    private fun messages(date: String) =
        db.collection("sessions").document(date).collection("messages")

    /** 오늘 시스템 프롬프트를 세션에 올린다(웹/함수가 참고). fire-and-forget. */
    fun pushContext(date: String, context: String) {
        db.collection("sessions").document(date)
            .set(mapOf("context" to context, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    /** 폰에서 생긴 턴을 미러. source=android(웹/함수가 이건 무시). fire-and-forget. */
    fun pushTurn(date: String, role: String, text: String) {
        messages(date).add(
            mapOf(
                "role" to role,
                "text" to text,
                "source" to "android",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    /** 세션 메시지 실시간 구독. 스냅샷마다 전체 목록을 시간순으로 콜백. */
    fun listen(date: String, onMessages: (List<Remote>) -> Unit): ListenerRegistration =
        messages(date).orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { d ->
                    val role = d.getString("role") ?: return@mapNotNull null
                    val text = d.getString("text") ?: return@mapNotNull null
                    Remote(role, text, d.getString("source") ?: "")
                }
                onMessages(list)
            }
}
