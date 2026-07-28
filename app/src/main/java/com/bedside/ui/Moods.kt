package com.bedside.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 하루 무드. 일기 본문엔 이모지·감정 라벨을 넣지 않지만(프롬프트 규칙), 무드는 본문과
 * 분리된 메타데이터라 UI에서 이모지로 보여도 된다. 저장은 라벨 단어로 한다.
 */
object Moods {
    data class Mood(val label: String, val emoji: String)

    val options = listOf(
        Mood("좋았어", "😄"),
        Mood("괜찮아", "🙂"),
        Mood("그냥", "😐"),
        Mood("지쳤어", "😮‍💨"),
        Mood("힘들었어", "😔"),
    )

    fun emojiFor(label: String?): String? =
        label?.let { l -> options.firstOrNull { it.label == l }?.emoji }
}

/** 무드 선택 칩 줄. 이미 고른 걸 다시 누르면 해제(null). */
@Composable
fun MoodPicker(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Moods.options.forEach { mood ->
            FilterChip(
                selected = selected == mood.label,
                onClick = { onSelect(if (selected == mood.label) null else mood.label) },
                label = { Text("${mood.emoji} ${mood.label}") },
            )
        }
    }
}
