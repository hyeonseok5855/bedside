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
 * Claude Messages API 클라이언트 (텍스트, 비스트리밍).
 *
 * 벤더 SDK 없이 HttpURLConnection + org.json으로 최소 구현. 스트리밍/TTS는 후순위.
 * 키는 BuildConfig(secrets.properties)에서 온다 — 소스에 하드코딩하지 않는다.
 */
object AnthropicClient {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val API_VERSION = "2023-06-01"
    // 대화·일기 생성은 Sonnet 5 (decisions #5).
    private const val MODEL = "claude-sonnet-5"

    class ApiException(message: String) : Exception(message)

    suspend fun complete(
        system: String,
        messages: List<ChatMessage>,
        maxTokens: Int = 1024,
    ): String = withContext(Dispatchers.IO) {
        val key = BuildConfig.ANTHROPIC_API_KEY
        if (key.isBlank()) throw ApiException("ANTHROPIC_API_KEY 없음 (secrets.properties 확인)")

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", maxTokens)
            put("system", system)
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
            readTimeout = 60_000
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

    private fun parseText(json: String): String {
        val content = JSONObject(json).optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        return sb.toString().trim()
    }
}
