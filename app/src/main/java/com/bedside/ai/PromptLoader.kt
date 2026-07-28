package com.bedside.ai

import android.content.Context

/**
 * 프롬프트를 앱 자산에서 읽는다. 원본 단일 출처는 docs/prompts (빌드 시 복사됨).
 *
 * 프롬프트 마크다운은 설명 + ``` 코드펜스 안의 실제 시스템 프롬프트로 이뤄진다.
 * 여기서는 첫 코드펜스 안의 텍스트만 뽑는다.
 */
object PromptLoader {

    fun interviewer(context: Context): String = fencedBlock(read(context, "interviewer.md"))
    fun diaryWriter(context: Context): String = fencedBlock(read(context, "diary-writer.md"))

    private fun read(context: Context, name: String): String =
        context.assets.open("prompts/$name").bufferedReader().use { it.readText() }

    /** 첫 ``` ... ``` 코드펜스의 내용. 없으면 원문 전체. (테스트 노출) */
    internal fun fencedBlock(markdown: String): String {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("```") }
        if (start < 0) return markdown.trim()
        val end = lines.drop(start + 1).indexOfFirst { it.trimStart().startsWith("```") }
        if (end < 0) return markdown.trim()
        return lines.subList(start + 1, start + 1 + end).joinToString("\n").trim()
    }
}
