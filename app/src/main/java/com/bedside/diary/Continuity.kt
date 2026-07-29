package com.bedside.diary

import android.content.Context
import com.bedside.ai.OpenThreads
import com.bedside.ai.PersonaMemory
import com.bedside.data.Db
import java.time.LocalDate

/**
 * 대화의 연속성 재료. 오늘 센서(DayBriefing)와 별개로, "지난 밤들"에서 이어갈 실마리를
 * 모은다 — 최근 일기, 접어둔 [heavy] 주제, 앱이 학습한 사실. 인터뷰어가 후속 질문을
 * 던질 수 있게 한다. 나열하지 말고 실마리로만 쓰라는 지시는 프롬프트가 담당.
 */
object Continuity {

    suspend fun build(context: Context, today: LocalDate): String {
        val learned = PersonaMemory.load(context)
        val recent = DiaryFiles.recentBodies(context, today.toString(), 2)
        val heavy = recentHeavyTopics(context, today)
        val recall = DiaryFiles.onThisDay(context, today).lastOrNull()
            ?.let { it.label to DiaryFiles.read(context, it.date).take(800) }
        val rendered = render(learned, recent, heavy, recall)
        val threads = OpenThreads.forContext(context, today)
        return if (threads.isBlank()) rendered else (rendered + "\n\n" + threads).trim()
    }

    /** 순수 조립부. DB/파일 접근과 분리해 단위 테스트한다. */
    internal fun render(
        learned: String,
        recentDiaries: List<String>,
        heavyTopics: List<String>,
        recall: Pair<String, String>? = null,
    ): String {
        val sb = StringBuilder()

        if (recall != null) {
            sb.append("# 그때의 오늘 (").append(recall.first)
                .append(" 오늘의 일기 — 대화에 자연스럽게 떠올려 줘도 좋음)\n")
                .append(recall.second).append("\n\n")
        }

        if (learned.isNotBlank()) {
            sb.append("# 그동안 알게 된 것 (배경, 나열 금지)\n").append(learned).append("\n\n")
        }

        if (recentDiaries.isNotEmpty()) {
            sb.append("# 최근 일기 (이어가기용 — 나열 말고 후속 질문의 실마리로만)\n")
            recentDiaries.forEachIndexed { i, body ->
                sb.append("\n[일기 ").append(i + 1).append("]\n").append(body.take(1200)).append('\n')
            }
            sb.append('\n')
        }

        if (heavyTopics.isNotEmpty()) {
            sb.append("# 지난 밤 접어둔 주제 — 라이징이 먼저 열면 조심스럽게 이어가도 됨\n")
            heavyTopics.forEach { sb.append("- ").append(it).append('\n') }
        }

        return sb.toString().trim()
    }

    /** 최근 2주 assistant 메시지 중 [heavy]가 붙은 문장. 태그는 떼고 문장만. */
    private suspend fun recentHeavyTopics(context: Context, today: LocalDate): List<String> {
        val since = today.minusDays(14).toString()
        return Db.get(context).messages().since(since)
            .filter { it.role == "assistant" && it.text.contains("[heavy]") }
            .flatMap { msg ->
                msg.text.lineSequence()
                    .filter { it.contains("[heavy]") }
                    .map { it.replace("[heavy]", "").trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
            .distinct()
            .takeLast(5)
    }
}
