package com.bedside.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/** 프롬프트 마크다운에서 코드펜스 추출 순수 로직. */
class PromptLoaderTest {

    @Test
    fun extractsFirstFencedBlock() {
        val md = "설명 문장\n\n```\n실제 프롬프트\n여러 줄\n```\n\n뒤 설명"
        assertEquals("실제 프롬프트\n여러 줄", PromptLoader.fencedBlock(md))
    }

    @Test
    fun handlesLanguageHintOnFence() {
        val md = "```markdown\n내용만\n```"
        assertEquals("내용만", PromptLoader.fencedBlock(md))
    }

    @Test
    fun onlyTheFirstBlock() {
        val md = "```\nA\n```\n중간\n```\nB\n```"
        assertEquals("A", PromptLoader.fencedBlock(md))
    }

    @Test
    fun noFenceReturnsWholeTrimmed() {
        assertEquals("그냥 텍스트", PromptLoader.fencedBlock("  그냥 텍스트  "))
    }

    @Test
    fun unterminatedFenceReturnsWhole() {
        val md = "```\n끝 없음"
        assertEquals("```\n끝 없음", PromptLoader.fencedBlock(md))
    }
}
