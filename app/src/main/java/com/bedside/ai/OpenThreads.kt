package com.bedside.ai

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * '열린 일' — 라이징이 하던/앞둔 것 중 나중에 결과를 물어볼 만한 것들(결정 48).
 * 일기 저장 때 대화에서 뽑아 쌓고, 대화 맥락에 넣어 AI가 잔소리 없이 슬쩍 후속을 묻게 한다.
 * 라이징의 '도전→중단' 패턴에 맞춰, 시작한 걸 조용히 이어봐 주는 장치.
 */
object OpenThreads {

    private const val KEEP_DAYS = 14L
    private const val MAX = 12

    data class Thread(val date: LocalDate, val text: String)

    private fun file(context: Context): File = File(context.filesDir, "open_threads.md")

    fun load(context: Context): List<Thread> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val d = runCatching { LocalDate.parse(parts[0].trim()) }.getOrNull() ?: return@mapNotNull null
            val t = parts[1].trim()
            if (t.isEmpty()) null else Thread(d, t)
        }
    }

    /** 새 열린 일들을 오늘 날짜로 추가(중복 제외). 오래된(14일↑) 건 이 참에 정리. */
    fun addFrom(context: Context, today: LocalDate, bullets: List<String>) {
        val clean = bullets.map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() && !it.equals("없음", ignoreCase = true) }
        val kept = load(context).filter { ChronoUnit.DAYS.between(it.date, today) < KEEP_DAYS }
        val keptTexts = kept.map { it.text }.toSet()
        val fresh = clean.filter { it !in keptTexts }.map { Thread(today, it) }
        val all = (kept + fresh).takeLast(MAX)
        file(context).writeText(all.joinToString("\n") { "${it.date} | ${it.text}" })
    }

    /** 대화 컨텍스트용. 14일 내 열린 일들. 비면 "". */
    fun forContext(context: Context, today: LocalDate): String {
        val recent = load(context).filter { ChronoUnit.DAYS.between(it.date, today) < KEEP_DAYS }
        if (recent.isEmpty()) return ""
        return buildString {
            append("# 이어서 물어볼 것 (열린 일 — 라이징이 하던/앞둔 것. 잔소리 말고 관심으로 슬쩍)\n")
            recent.forEach { t ->
                val days = ChronoUnit.DAYS.between(t.date, today)
                val ago = if (days <= 0) "오늘" else "${days}일 전"
                append("- ").append(t.text).append(" (").append(ago).append(")\n")
            }
        }
    }
}
