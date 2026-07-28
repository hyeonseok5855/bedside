package com.bedside

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bedside.ai.AnthropicClient
import com.bedside.ai.ChatMessage
import com.bedside.ai.PromptLoader
import com.bedside.ai.Task
import com.bedside.diary.DiaryFiles
import com.bedside.diary.ReviewFiles
import com.bedside.ui.MarkdownText
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 주간·월간 돌아보기. 여러 일기를 가로질러 흐름을 읽어준다(결정 33). 압박이 아니라
 * '내가 이만큼 남겼다'는 증거로서의 회고 — 목표 처방 없이 담담하게.
 */
class ReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReviewScreen()
                }
            }
        }
    }
}

@Composable
private fun ReviewScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var content by remember { mutableStateOf("") }
    var currentId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var past by remember { mutableStateOf<List<ReviewFiles.Entry>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        past = ReviewFiles.list(context)
    }

    fun open(id: String) {
        content = ReviewFiles.read(context, id)
        currentId = id
        status = ""
    }

    fun generate(kind: ReviewFiles.Kind, force: Boolean) {
        if (busy) return
        scope.launch {
            val today = LocalDate.now()
            val id = when (kind) {
                ReviewFiles.Kind.WEEK -> ReviewFiles.weekId(today)
                ReviewFiles.Kind.MONTH -> ReviewFiles.monthId(today)
            }
            if (!force && ReviewFiles.exists(context, id)) {
                open(id)
                return@launch
            }
            val range = when (kind) {
                ReviewFiles.Kind.WEEK -> ReviewFiles.weekRange(today)
                ReviewFiles.Kind.MONTH -> ReviewFiles.monthRange(today)
            }
            val diaries = DiaryFiles.bodiesInRange(context, range.first.toString(), range.second.toString())
            if (diaries.isEmpty()) {
                status = "이 기간에 쓴 일기가 없어요."
                content = ""
                return@launch
            }
            busy = true
            status = "돌아보는 글을 쓰고 있어요... (조금 걸려요)"
            try {
                val transcript = diaries.joinToString("\n\n---\n\n") { (d, b) -> "[$d]\n$b" }
                val md = AnthropicClient.complete(
                    task = Task.REVIEW,
                    system = PromptLoader.reviewWriter(context),
                    messages = listOf(ChatMessage("user", "다음은 이 기간에 쓴 일기들이야. 돌아보는 글을 써줘.\n\n$transcript")),
                )
                ReviewFiles.save(context, id, md)
                content = md
                currentId = id
                status = ""
                refreshTick++
            } catch (t: Throwable) {
                status = "회고 오류: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("돌아보기", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { generate(ReviewFiles.Kind.WEEK, force = false) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("이번 주") }
            OutlinedButton(
                onClick = { generate(ReviewFiles.Kind.MONTH, force = false) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("이번 달") }
        }

        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        if (content.isNotEmpty()) {
            MarkdownText(content)
            if (!busy) {
                TextButton(onClick = {
                    val kind = if (currentId.contains("-W")) ReviewFiles.Kind.WEEK else ReviewFiles.Kind.MONTH
                    generate(kind, force = true)
                }) { Text("다시 만들기") }
            }
        }

        if (past.isNotEmpty()) {
            Text("지난 회고", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            past.forEach { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open(entry.id) },
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(labelFor(entry.id), style = MaterialTheme.typography.titleSmall)
                        if (entry.preview.isNotBlank()) {
                            Text(entry.preview, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** "2026-W30" → "2026년 30주차", "2026-07" → "2026년 7월". */
private fun labelFor(id: String): String {
    val wIdx = id.indexOf("-W")
    return if (wIdx >= 0) {
        "${id.take(4)}년 ${id.substring(wIdx + 2).toInt()}주차"
    } else {
        val parts = id.split("-")
        if (parts.size == 2) "${parts[0]}년 ${parts[1].toInt()}월" else id
    }
}
