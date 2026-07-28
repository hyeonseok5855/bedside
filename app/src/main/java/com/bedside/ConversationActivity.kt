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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

    var systemPrompt by remember { mutableStateOf("") }
    var turns by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("준비 중...") }
    var diary by remember { mutableStateOf<String?>(null) }

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
                status = "이어서 대화"
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
            status = "일기 쓰는 중..."
            try {
                val dw = PromptLoader.diaryWriter(context)
                val transcript = turns.joinToString("\n") {
                    (if (it.role == "assistant") "상대" else "나") + ": " + it.text
                }
                val md = AnthropicClient.complete(
                    task = Task.DIARY,
                    system = dw,
                    messages = listOf(ChatMessage("user", "다음 대화를 바탕으로 오늘 일기를 써줘.\n\n$transcript")),
                )
                val file = DiaryFiles.save(context, today, md)
                diary = md
                status = "일기 저장됨: ${file.absolutePath}"
            } catch (t: Throwable) {
                status = "일기 오류: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("bedside — 오늘 밤 ($dateStr)", style = MaterialTheme.typography.titleMedium)
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            turns.forEach { m ->
                val mine = m.role == "user"
                Text(
                    text = (if (mine) "나  " else "· ") + m.text,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (mine) TextAlign.End else TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            diary?.let {
                HorizontalDivider()
                Text("── 오늘 일기 ──", style = MaterialTheme.typography.titleSmall)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

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

        OutlinedButton(
            onClick = { writeDiary() },
            enabled = !busy && turns.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("일기 쓰기") }
    }
}
