package com.bedside

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bedside.ai.AnthropicClient
import com.bedside.ai.ChatMessage
import com.bedside.ai.PromptLoader
import com.bedside.ai.Task
import com.bedside.data.Db
import com.bedside.data.Message
import com.bedside.diary.DayBriefing
import com.bedside.diary.DiaryFiles
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 텍스트 기반 야간 대화 화면. 인터뷰어 프롬프트 + 오늘 브리핑으로 첫 질문을 받고,
 * 사용자가 답하면 이어 간다. 매 턴 즉시 DB에 저장(결정 13). "일기 쓰기"로 생성·저장.
 * STT/TTS는 후순위 — 지금은 키보드 입력.
 */
class ConversationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConversationScreen()
                }
            }
        }
    }
}

private const val KICKOFF = "오늘 밤 대화를 시작합니다. 브리핑을 참고해 첫 질문을 하나만 해주세요."

@Composable
private fun ConversationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val dateStr = remember { today.toString() }
    val scrollState = rememberScrollState()

    var systemPrompt by remember { mutableStateOf("") }
    var turns by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("준비 중...") }
    var writing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    suspend fun persist(role: String, text: String) {
        Db.get(context).messages().insert(
            Message(sessionDate = dateStr, role = role, text = text, createdAt = System.currentTimeMillis()),
        )
    }

    LaunchedEffect(Unit) {
        try {
            val interviewer = PromptLoader.interviewer(context)
            val briefing = DayBriefing.build(context, today)
            systemPrompt = interviewer + "\n\n# 오늘 브리핑\n" + briefing

            val stored = Db.get(context).messages().forSession(dateStr)
                .map { ChatMessage(it.role, it.text) }
            if (stored.isNotEmpty()) {
                turns = stored
                status = ""
            } else {
                busy = true
                status = "첫 질문 받는 중..."
                val first = AnthropicClient.complete(Task.CONVERSATION, systemPrompt, listOf(ChatMessage("user", KICKOFF)))
                persist("assistant", first)
                turns = listOf(ChatMessage("assistant", first))
                status = ""
            }
        } catch (t: Throwable) {
            status = "오류: ${t.message}"
        } finally {
            busy = false
        }
    }

    // 새 말풍선이 생기거나 상태가 바뀌면 맨 아래로.
    LaunchedEffect(turns.size, busy) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        scope.launch {
            busy = true
            status = ""
            persist("user", text)
            turns = turns + ChatMessage("user", text)
            try {
                val apiMessages = listOf(ChatMessage("user", KICKOFF)) + turns
                val reply = AnthropicClient.complete(Task.CONVERSATION, systemPrompt, apiMessages)
                persist("assistant", reply)
                turns = turns + ChatMessage("assistant", reply)
            } catch (t: Throwable) {
                status = "오류: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    fun writeDiary() {
        if (busy || turns.isEmpty()) return
        scope.launch {
            busy = true
            writing = true
            status = "오늘 일기를 쓰고 있어요..."
            try {
                val dw = PromptLoader.diaryWriter(context)
                val transcript = turns.joinToString("\n") {
                    (if (it.role == "assistant") "상대" else "나") + ": " + it.text
                }
                // 최근 일기(수정본 포함)를 문체 레퍼런스로 곁들인다. 결정 23.
                val recent = DiaryFiles.recentBodies(context, dateStr, 3)
                val styleRef = if (recent.isEmpty()) "" else buildString {
                    append("\n\n# 문체 참고 (내가 쓰거나 고친 최근 일기 — 톤·문장 길이만 참고, 내용은 가져오지 마)\n")
                    recent.forEachIndexed { i, body ->
                        append("\n--- 참고 ${i + 1} ---\n").append(body).append('\n')
                    }
                }
                val md = AnthropicClient.complete(
                    task = Task.DIARY,
                    system = dw,
                    messages = listOf(ChatMessage("user", "다음 대화를 바탕으로 오늘 일기를 써줘.\n\n$transcript$styleRef")),
                )
                DiaryFiles.save(context, today, md)
                done = true
                status = "오늘 일기를 저장했어요."
            } catch (t: Throwable) {
                status = "일기 오류: ${t.message}"
            } finally {
                busy = false
                writing = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("오늘 밤", style = MaterialTheme.typography.titleMedium)
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            turns.forEach { m -> Bubble(text = m.text, mine = m.role == "user") }
            if (busy && !writing) TypingBubble()
        }

        if (done) {
            Button(
                onClick = {
                    context.startActivity(
                        android.content.Intent(context, DiaryViewerActivity::class.java)
                            .putExtra(DiaryViewerActivity.EXTRA_DATE, dateStr),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("오늘 일기 보기") }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    placeholder = { Text("답을 적어요") },
                )
                Button(onClick = { send() }, enabled = !busy && input.isNotBlank()) { Text("보내기") }
            }

            TextButton(
                onClick = { writeDiary() },
                enabled = !busy && turns.size >= 2,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("이제 그만 · 오늘 일기 쓰기") }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = "…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
