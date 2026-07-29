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
enum class Task { CONVERSATION, DIARY, REVIEW, SUGGEST }

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
        // 대화: Sonnet 5 보통(medium). thinking은 끈다(생략 시 adaptive가 켜지므로 명시).
        Task.CONVERSATION -> Profile(
            model = "claude-sonnet-5",
            maxTokens = 1024,
            thinking = "disabled",
            effort = "medium",
            readTimeoutMs = 60_000,
        )
        // 답변 추천: 대화와 같은 Sonnet 5 보통. 짧게 여러 개라 max_tokens만 줄인다.
        Task.SUGGEST -> Profile(
            model = "claude-sonnet-5",
            maxTokens = 400,
            thinking = "disabled",
            effort = "medium",
            readTimeoutMs = 30_000,
        )
        // 일기: 깊게. 하루 1회, 종합·성찰이 필요하니 Opus 4.8 + adaptive thinking.
        Task.DIARY -> Profile(
            model = "claude-opus-4-8",
            maxTokens = 8192, // thinking이 토큰을 먹으므로 넉넉히
            thinking = "adaptive",
            effort = "high",
            readTimeoutMs = 120_000,
        )
        // 주간/월간 회고: 여러 일기를 가로질러 흐름·패턴을 읽는다. 일기와 같은 깊은 프로필.
        Task.REVIEW -> Profile(
            model = "claude-opus-4-8",
            maxTokens = 8192,
            thinking = "adaptive",
            effort = "high",
            readTimeoutMs = 120_000,
        )
    }

    class ApiException(message: String) : Exception(message)

    private const val TOOL_VIEW = "view_photos"

    /** 특정 번호의 사진들을 (라벨, base64 JPEG)로 돌려준다. AI가 view_photos를 부를 때 호출된다. */
    fun interface PhotoTool {
        suspend fun view(indices: List<Int>): List<Pair<String, String>>
    }

    /** 텍스트 단발 호출(일기·회고·추천·알림). */
    suspend fun complete(
        task: Task,
        system: String,
        messages: List<ChatMessage>,
    ): String = withContext(Dispatchers.IO) {
        val profile = profileFor(task)
        val body = baseBody(profile, system).apply {
            put(
                "messages",
                JSONArray().apply {
                    messages.forEach { put(JSONObject().put("role", it.role).put("content", it.text)) }
                },
            )
        }
        parseText(postJson(body, profile.readTimeoutMs))
    }

    /**
     * 대화용. AI는 시스템 프롬프트의 '오늘 사진 목록'(메타데이터)을 보고, 필요하면
     * view_photos 도구로 실제 이미지를 열어 본다(결정 44). 도구를 안 부르면 사진은 전송되지
     * 않는다. photoTool이 null이면 도구 없이 텍스트만.
     */
    suspend fun completeConversation(
        system: String,
        messages: List<ChatMessage>,
        photoTool: PhotoTool?,
    ): String = withContext(Dispatchers.IO) {
        val profile = profileFor(Task.CONVERSATION)
        val msgs = JSONArray()
        messages.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }

        var rounds = 0
        var out: String
        while (true) {
            rounds++
            val body = baseBody(profile, system).apply {
                put("messages", msgs)
                if (photoTool != null) put("tools", JSONArray().put(viewPhotosTool()))
            }
            val resp = JSONObject(postJson(body, profile.readTimeoutMs))
            val content = resp.optJSONArray("content") ?: JSONArray()

            if (photoTool != null && resp.optString("stop_reason") == "tool_use" && rounds <= 3) {
                msgs.put(JSONObject().put("role", "assistant").put("content", content))
                msgs.put(JSONObject().put("role", "user").put("content", toolResults(content, photoTool)))
                continue
            }
            out = parseTextBlocks(content)
            break
        }
        out
    }

    /** tool_use 블록들을 실행해 tool_result(이미지 포함) 배열을 만든다. */
    private suspend fun toolResults(content: JSONArray, photoTool: PhotoTool): JSONArray {
        val results = JSONArray()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") != "tool_use" || block.optString("name") != TOOL_VIEW) continue
            val idxArr = block.optJSONObject("input")?.optJSONArray("indices")
            val indices = buildList {
                if (idxArr != null) for (j in 0 until idxArr.length()) add(idxArr.optInt(j))
            }
            val photos = runCatching { photoTool.view(indices) }.getOrDefault(emptyList())
            val rc = JSONArray()
            if (photos.isEmpty()) {
                rc.put(JSONObject().put("type", "text").put("text", "해당 사진을 열 수 없음"))
            } else {
                photos.forEach { (label, b64) ->
                    rc.put(JSONObject().put("type", "text").put("text", label))
                    rc.put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64),
                        ),
                    )
                }
            }
            results.put(
                JSONObject().put("type", "tool_result").put("tool_use_id", block.optString("id")).put("content", rc),
            )
        }
        return results
    }

    private fun baseBody(profile: Profile, system: String): JSONObject =
        JSONObject().apply {
            put("model", profile.model)
            put("max_tokens", profile.maxTokens)
            put("system", system)
            put("thinking", JSONObject().put("type", profile.thinking))
            put("output_config", JSONObject().put("effort", profile.effort))
        }

    private fun viewPhotosTool(): JSONObject =
        JSONObject().apply {
            put("name", TOOL_VIEW)
            put(
                "description",
                "오늘 찍은 사진 목록 중 특정 번호의 사진을 실제 이미지로 열어 본다. 대화 맥락상 " +
                    "사진 내용을 꼭 확인해야 할 때만 호출하라. 필요 없으면 절대 호출하지 마라.",
            )
            put(
                "input_schema",
                JSONObject().apply {
                    put("type", "object")
                    put(
                        "properties",
                        JSONObject().put(
                            "indices",
                            JSONObject().apply {
                                put("type", "array")
                                put("items", JSONObject().put("type", "integer"))
                                put("description", "열어 볼 사진 번호들. 예: [1, 3]")
                            },
                        ),
                    )
                    put("required", JSONArray().put("indices"))
                },
            )
        }

    private fun postJson(body: JSONObject, readTimeoutMs: Int): String {
        val key = BuildConfig.ANTHROPIC_API_KEY
        if (key.isBlank()) throw ApiException("ANTHROPIC_API_KEY 없음 (secrets.properties 확인)")
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", API_VERSION)
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException("HTTP $code: ${text.take(300)}")
            text
        } finally {
            conn.disconnect()
        }
    }

    /** content 배열에서 text 블록만 이어붙인다. thinking 블록은 무시. (테스트 노출) */
    internal fun parseText(json: String): String =
        parseTextBlocks(JSONObject(json).optJSONArray("content") ?: JSONArray())

    private fun parseTextBlocks(content: JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        return sb.toString().trim()
    }
}
