package com.bedside

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bedside.ai.AnthropicClient
import com.bedside.ai.ChatMessage
import com.bedside.ai.Task
import com.bedside.diary.DiaryFiles
import com.bedside.ui.Moods
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 수면과 기분의 흐름을 보여준다(결정 49). 일기와 함께 쌓인 수면 통계 + 무드를 엮음. */
class InsightsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { InsightsScreen() }
            }
        }
    }
}

private data class DayRow(val date: String, val stats: DiaryFiles.Stats?, val mood: String?)

@Composable
private fun InsightsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val rowFmt = remember { DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN) }

    var days by remember { mutableStateOf<List<DayRow>>(emptyList()) }
    var insight by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        days = (0..20L).mapNotNull { off ->
            val date = today.minusDays(off).toString()
            val s = DiaryFiles.getStats(context, date)
            val m = DiaryFiles.getMood(context, date)
            if (s == null && m == null) null else DayRow(date, s, m)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("수면과 기분", style = MaterialTheme.typography.titleLarge)

        val withSleep = days.filter { it.stats != null }
        if (withSleep.isNotEmpty()) {
            val avgSleep = withSleep.map { it.stats!!.sleepMin }.average().toInt()
            val avgDeep = withSleep.map { it.stats!!.deepMin }.average().toInt()
            val tired = days.count { it.mood == "지쳤어" || it.mood == "힘들었어" }
            val summary = buildString {
                append("최근 ").append(days.size).append("일 · 평균 수면 ")
                append(avgSleep / 60).append("시간 ").append(avgSleep % 60).append("분, 깊은잠 ").append(avgDeep).append("분")
                if (tired > 0) append(" · 지친 날 ").append(tired).append("일")
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "아직 데이터가 쌓이는 중이에요. 며칠 기록하면 수면과 기분의 흐름이 보여요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (withSleep.size >= 3) {
            OutlinedButton(
                onClick = { scope.launch { busy = true; insight = generateInsight(context, days); busy = false } },
                enabled = !busy,
            ) { Text(if (busy) "보는 중…" else "패턴 봐줘") }
            if (insight.isNotEmpty()) {
                Text(insight, style = MaterialTheme.typography.bodyMedium)
            }
        }

        days.forEach { day ->
            val label = runCatching { LocalDate.parse(day.date).format(rowFmt) }.getOrDefault(day.date)
            val emoji = Moods.emojiFor(day.mood)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (emoji != null) Text(emoji, style = MaterialTheme.typography.titleMedium)
                }
                day.stats?.let { s ->
                    SleepBar(s.sleepMin, s.deepMin)
                    Text(
                        "${s.sleepMin / 60}시간 ${s.sleepMin % 60}분 · 깊은잠 ${s.deepMin}분 · 깸 ${s.awakeMin}분",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** 수면 시간 막대(10시간 기준). 채워진 부분은 총 수면, 진한 부분은 깊은잠 비율. */
@Composable
private fun SleepBar(sleepMin: Int, deepMin: Int) {
    val maxMin = 600f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((sleepMin / maxMin).coerceIn(0f, 1f))
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private suspend fun generateInsight(context: android.content.Context, days: List<DayRow>): String {
    val data = days.joinToString("\n") { d ->
        val s = d.stats
        d.date + ": " +
            (s?.let { "수면 ${it.sleepMin}분(깊은잠 ${it.deepMin}, 깸 ${it.awakeMin})" } ?: "수면기록없음") +
            (d.mood?.let { ", 기분 $it" } ?: "")
    }
    return runCatching {
        AnthropicClient.complete(
            task = Task.CONVERSATION,
            system = "다음은 라이징의 최근 수면·기분 기록이다. 수면(양·깊은잠·깬 정도)과 기분/컨디션 " +
                "사이에 보이는 흐름이나 패턴이 있으면 2~3문장으로 담담하게 짚어줘라. 억지 인과나 훈계 없이, " +
                "데이터가 적으면 적은 대로. 라이징에게 말 걸듯 2인칭으로.",
            messages = listOf(ChatMessage("user", data)),
        )
    }.getOrElse { "지금은 패턴을 보기 어려워요. 며칠 더 쌓이면 다시 봐줄게요." }
}
