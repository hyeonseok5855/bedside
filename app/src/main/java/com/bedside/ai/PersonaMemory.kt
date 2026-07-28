package com.bedside.ai

import android.content.Context
import java.io.File

/**
 * 앱이 대화하면서 라이징에 대해 새로 알게 된, 오래 유지될 사실들을 쌓아둔다.
 * 정적 프로필(persona.md)을 보완하는 "학습분"이다. 기기 내부 저장소에만 있고
 * 사용자가 원하면 나중에 열람·정리할 수 있게 마크다운 불릿으로 둔다.
 */
object PersonaMemory {

    private fun file(context: Context): File = File(context.filesDir, "persona_learned.md")

    fun load(context: Context): String {
        val f = file(context)
        return if (f.exists()) f.readText().trim() else ""
    }

    /** 새 사실 불릿들을 중복 없이 덧붙인다. 이미 있는 줄은 건너뛴다. */
    fun append(context: Context, bullets: List<String>) {
        val clean = bullets.map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() && !it.equals("없음", ignoreCase = true) }
        if (clean.isEmpty()) return

        val f = file(context)
        val existing = if (f.exists()) f.readText() else ""
        val existingLines = existing.lineSequence().map { it.trim().removePrefix("-").trim() }.toSet()
        val fresh = clean.filter { it !in existingLines }
        if (fresh.isEmpty()) return

        val sb = StringBuilder(existing)
        if (existing.isNotEmpty() && !existing.endsWith("\n")) sb.append('\n')
        fresh.forEach { sb.append("- ").append(it).append('\n') }
        f.writeText(sb.toString())
    }
}
