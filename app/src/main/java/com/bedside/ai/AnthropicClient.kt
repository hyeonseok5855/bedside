package com.bedside.ai

import com.bedside.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 대화 한 턴. */
data class ChatMessage(val role: String, val text: String)

/**
 * 호출 용도. 모델과 사고 깊이를 여기서 가른다(결정 29).
 * - CONVERSATION: 속도 우선. thinking 끄고 effort 낮게.
 * - DIARY: 깊은 사고 우선. adaptive thinking + 높은 effort, 시간이 걸려도 됨.
 */
enum class Task { CONVERSATION, DIARY }

/**
 * Claude Messages API 클라이언트 (텍스트, 비스트리밍).
 *
 * 벤더 SDK 없이 HttpURLConnection + org.json으로 최소 구현. STT/TTS는 넣지 않는다(결정 30).
 * 키는 BuildConfig(secrets.properties)에서 온다 — 소스에 하드코딩하지 않는다.
 *
 * 주의: 이 모델들에서 budget_tokens/temperature 등은 제거되어 보내면 400.
 * 사고 깊이는 thinking.type + output_config.effort로만 제어한다.
 */
object AnthropicClient {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val API_VERSION = "2023-06-01"

    private data class Profile(
        val model: String,
        val maxTokens: Int,
        /** "disabled" | "adaptive" */
        val thinking: String,
        /** "low" | "medium" | "high" | "xhigh" | "max" */
        val effort: String,
        val readTimeoutMs: Int,
    )

    private fun profileFor(task: Task): Profile = when (task) {
        // 대화: 빠르게. Sonnet 5는 thinking 생략 시 adaptive가 켜지므로 명시적으로 끈다.
        Task.CONVERSATION -> Profile(
            model = "claude-sonnet-5",
            maxTokens = 1024,
            thinking = "disabled",
            effort = "low",
            readTimeoutMs = 60_000,
        )
        // 일기: 깊게. 하루 1회, 종합·성찰이 필요하니 Opus 4.8 + adaptive thinking.
        Task.DIARY -> Profile(
            model = "claude-opus-4-8",
            maxTokens = 8192, // thinking이 토큰을 먹으므로 넉넉히
            thinking = "adaptive",
            effort = "high",
            readTimeoutMs = 120_000,
        )
    }

    class ApiException(message: String) : Exception(message)

    suspend fun complete(
        task: Task,
        system: String,
        messages: List<ChatMessage>,
    ): String = withContext(Dispatchers.IO) {
        val key = BuildConfig.ANTHROPIC_API_KEY
        if (key.isBlank()) throw ApiException("ANTHROPIC_API_KEY 없음 (secrets.properties 확인)")

        val profile = profileFor(task)

        val body = JSONObject().apply {
            put("model", profile.model)
            put("max_tokens", profile.maxTokens)
            put("system", system)
            put("thinking", JSONObject().put("type", profile.thinking))
            put("output_config", JSONObject().put("effort", profile.effort))
            put(
                "messages",
                JSONArray().apply {
                    messages.forEach { m ->
                        put(
                            JSONObject().apply {
                                put("role", m.role)
                                put("content", m.text)
                            },
                        )
                    }
                },
            )
        }

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = profile.readTimeoutMs
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", API_VERSION)
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw ApiException("HTTP $code: ${text.take(300)}")
            }
            parseText(text)
        } finally {
            conn.disconnect()
        }
    }

    /** content 배열에서 text 블록만 이어붙인다. thinking 블록은 무시. (테스트 노출) */
    internal fun parseText(json: String): String {
        val content = JSONObject(json).optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        return sb.toString().trim()
    }
}
