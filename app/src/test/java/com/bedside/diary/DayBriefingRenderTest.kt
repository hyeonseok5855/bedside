package com.bedside.diary

import com.bedside.data.CollectedEvent
import org.junit.Assert.assertTrue
import org.junit.Test

/** 브리핑 렌더 순수 로직 (DB 없음). */
class DayBriefingRenderTest {

    private fun ev(source: String, type: String, value: String?, label: String?, at: Long) =
        CollectedEvent(source = source, type = type, label = label, value = value, occurredAt = at, recordedAt = at)

    @Test
    fun emptyGivesGenericStartPrompt() {
        val s = DayBriefing.render(emptyList())
        assertTrue(s.contains("통상적인 질문"))
    }

    @Test
    fun rendersEachSourceLabel() {
        val s = DayBriefing.render(
            listOf(
                ev("geofence", "exit", null, "회사", 1000),
                ev("geofence", "enter", null, "집", 2000),
                ev("steps", "snapshot", "745", null, 3000),
                ev("photo", "snapshot", "2장", null, 4000),
                ev("weight", "snapshot", "70.5", null, 5000),
            ),
        )
        assertTrue(s.startsWith("오늘의 타임라인"))
        assertTrue(s.contains("회사 이탈"))
        assertTrue(s.contains("집 도착"))
        assertTrue(s.contains("걸음 745"))
        assertTrue(s.contains("사진 2장"))
        assertTrue(s.contains("몸무게 70.5kg"))
    }

    @Test
    fun sortsByOccurredTimeRegardlessOfInputOrder() {
        val s = DayBriefing.render(
            listOf(
                ev("weight", "snapshot", "70", null, 5000),
                ev("geofence", "exit", null, "회사", 1000),
                ev("steps", "snapshot", "100", null, 3000),
            ),
        )
        val idxGeo = s.indexOf("회사 이탈")
        val idxSteps = s.indexOf("걸음 100")
        val idxWeight = s.indexOf("몸무게")
        assertTrue("회사(1000) < 걸음(3000)", idxGeo in 0 until idxSteps)
        assertTrue("걸음(3000) < 몸무게(5000)", idxSteps in 0 until idxWeight)
    }

    @Test
    fun unknownSourceFallsBack() {
        val s = DayBriefing.render(listOf(ev("mood", "snapshot", "좋음", null, 1000)))
        assertTrue(s.contains("mood 좋음"))
    }
}
