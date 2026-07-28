package com.bedside.ai

import com.bedside.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 실제 Claude API 통합 테스트 (토큰 사용). BuildConfig의 키가 없으면 스킵한다.
 * 대화/일기 프로필이 200으로 텍스트를 돌려주는지, 한글·멀티턴이 되는지 본다.
 */
class AnthropicApiTest {

    @Before
    fun requireKey() {
        assumeTrue("ANTHROPIC_API_KEY 없음 — API 테스트 스킵", BuildConfig.ANTHROPIC_API_KEY.isNotBlank())
    }

    @Test
    fun conversationProfileReturnsText() = runBlocking {
        val out = AnthropicClient.complete(
            task = Task.CONVERSATION,
            system = "You are terse. Answer in English, one word.",
            messages = listOf(ChatMessage("user", "Reply with the single word: pong")),
        )
        assertTrue("빈 응답", out.isNotBlank())
    }

    @Test
    fun diaryProfileReturnsText() = runBlocking {
        val out = AnthropicClient.complete(
            task = Task.DIARY,
            system = "You write a diary in one short sentence, first person.",
            messages = listOf(ChatMessage("user", "오늘 커피를 마셨고 마음이 차분했다. 한 문장 일기로 써줘.")),
        )
        assertTrue("빈 응답", out.isNotBlank())
    }

    @Test
    fun koreanRoundTripUtf8() = runBlocking {
        val out = AnthropicClient.complete(
            task = Task.CONVERSATION,
            system = "당신은 한국어로만 짧게 답합니다.",
            messages = listOf(ChatMessage("user", "'네'라고만 답해줘")),
        )
        assertTrue("빈 응답", out.isNotBlank())
    }

    @Test
    fun multiTurnAlternationAccepted() = runBlocking {
        val messages = listOf(
            ChatMessage("user", "(대화 시작) 첫 질문을 해줘"),
            ChatMessage("assistant", "오늘 하루 어땠어요?"),
            ChatMessage("user", "그냥 그랬어요."),
        )
        val out = AnthropicClient.complete(
            task = Task.CONVERSATION,
            system = "당신은 인터뷰어입니다. 한국어로 한 문장 질문만 하세요.",
            messages = messages,
        )
        assertTrue("빈 응답", out.isNotBlank())
    }
}
