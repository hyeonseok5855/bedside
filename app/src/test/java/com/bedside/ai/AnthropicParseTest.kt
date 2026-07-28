package com.bedside.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/** 응답 파싱 순수 로직 (네트워크 없음). */
class AnthropicParseTest {

    @Test
    fun concatenatesTextBlocks() {
        val json = """{"content":[{"type":"text","text":"안녕"},{"type":"text","text":" 라이징"}]}"""
        assertEquals("안녕 라이징", AnthropicClient.parseText(json))
    }

    @Test
    fun ignoresThinkingBlocks() {
        val json = """{"content":[{"type":"thinking","thinking":"내부추론"},{"type":"text","text":"답"}]}"""
        assertEquals("답", AnthropicClient.parseText(json))
    }

    @Test
    fun emptyWhenOnlyThinking() {
        val json = """{"content":[{"type":"thinking","thinking":"x"}]}"""
        assertEquals("", AnthropicClient.parseText(json))
    }

    @Test
    fun emptyWhenNoContentField() {
        assertEquals("", AnthropicClient.parseText("""{"id":"msg_1"}"""))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        val json = """{"content":[{"type":"text","text":"  hi  "}]}"""
        assertEquals("hi", AnthropicClient.parseText(json))
    }
}
