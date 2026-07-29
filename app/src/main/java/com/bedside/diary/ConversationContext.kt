package com.bedside.diary

import android.content.Context
import com.bedside.ai.ChatMessage
import com.bedside.ai.PersonaLoader
import com.bedside.ai.PromptLoader
import java.time.LocalDate

/**
 * 대화 시스템 프롬프트 조립을 한곳에. 대화 화면과 '틈틈이 알림'(백그라운드 생성)이
 * 같은 프롬프트를 쓰도록 공유한다.
 *
 * 조립: 인터뷰어 + 프로필 + 연속성 + 오늘 브리핑(정적 base) + 지금(NowContext, 매번).
 */
object ConversationContext {

    const val KICKOFF =
        "라이징이 방금 대화를 열었습니다. '지금'(시각·요일·위치)과 브리핑을 보고, " +
            "지금이 하루의 어느 지점인지 헤아려 자연스럽게 말을 걸어 주세요. 질문은 하나만."

    const val NUDGE =
        "지금 상황(시각·요일·위치·일정)에 맞춰, 라이징에게 먼저 슬쩍 말을 걸어 주세요. " +
            "아직 라이징은 이번에 답하지 않았습니다. 부담스럽지 않게 짧게, 질문 하나만."

    /** 정적 base — 세션 동안 잘 안 바뀌는 부분. */
    suspend fun base(context: Context, today: LocalDate): String {
        val interviewer = PromptLoader.interviewer(context)
        val persona = PersonaLoader.load(context)
        val continuity = Continuity.build(context, today)
        val briefing = DayBriefing.build(context, today)
        return buildString {
            append(interviewer)
            if (persona.isNotBlank()) append("\n\n").append(persona)
            if (continuity.isNotBlank()) append("\n\n").append(continuity)
            append("\n\n# 오늘 브리핑\n").append(briefing)
        }
    }

    /** base + 지금(시각·위치·일정). 매 호출마다 새로. */
    suspend fun system(context: Context, today: LocalDate): String =
        base(context, today) + "\n\n" + NowContext.build(context)

    /**
     * 연속된 같은 역할 메시지를 합친다. 알림이 사용자의 답 없이 assistant 턴을 여러 번
     * 쌓을 수 있어, API의 user/assistant 교대 규칙을 지키려면 합쳐야 한다.
     */
    fun normalize(messages: List<ChatMessage>): List<ChatMessage> {
        val out = mutableListOf<ChatMessage>()
        messages.forEach { m ->
            val last = out.lastOrNull()
            if (last != null && last.role == m.role) {
                out[out.lastIndex] = last.copy(text = last.text + "\n\n" + m.text)
            } else {
                out.add(m)
            }
        }
        return out
    }
}
