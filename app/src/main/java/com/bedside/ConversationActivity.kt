package com.bedside

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.core.view.WindowCompat
import com.bedside.ai.AnthropicClient
import com.bedside.ai.ChatMessage
import com.bedside.ai.PersonaMemory
import com.bedside.ai.PromptLoader
import com.bedside.ai.Task
import com.bedside.data.Db
import com.bedside.data.Message
import com.bedside.diary.ConversationContext
import com.bedside.diary.DiaryFiles
import com.bedside.diary.DiaryPhotos
import com.bedside.diary.NowContext
import com.bedside.log.TranscriptLog
import com.bedside.media.MediaStorePhotoReader
import com.bedside.media.PhotoVision
import com.bedside.ui.MoodPicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * 텍스트 기반 야간 대화 화면. 인터뷰어 프롬프트 + 오늘 브리핑으로 첫 질문을 받고,
 * 사용자가 답하면 이어 간다. 매 턴 즉시 DB에 저장(결정 13). "일기 쓰기"로 생성·저장.
 * STT/TTS는 후순위 — 지금은 키보드 입력.
 */
class ConversationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 입력창이 키보드에 가려지지 않게 IME 인셋을 직접 다룬다. adjustResize만으로는
        // Compose에서 안 먹어서, 창을 인셋까지 그리게 하고 루트에 imePadding을 준다.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConversationScreen()
                }
            }
        }
    }
}

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
    var mood by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingSuggestions by remember { mutableStateOf(false) }

    suspend fun persist(role: String, text: String) {
        Db.get(context).messages().insert(
            Message(sessionDate = dateStr, role = role, text = text, createdAt = System.currentTimeMillis()),
        )
        // 평문 대화 로그에도 남긴다(결정 36). 새 턴이 생기는 이 지점에서만.
        TranscriptLog.append(context, dateStr, role, text)
    }

    LaunchedEffect(Unit) {
        try {
            systemPrompt = ConversationContext.base(context, today)
            // 이 세션이 처음이면 평문 로그에 시스템 프롬프트를 헤더로 박는다.
            TranscriptLog.startIfNew(context, dateStr, systemPrompt)

            val stored = Db.get(context).messages().forSession(dateStr)
                .map { ChatMessage(it.role, it.text) }
            if (stored.isNotEmpty()) {
                turns = stored
                status = ""
            } else {
                busy = true
                status = "첫 질문 받는 중..."
                val sys = systemPrompt + "\n\n" + NowContext.build(context)
                val photos = PhotoVision.recentPhotos(context)
                val first = AnthropicClient.complete(Task.CONVERSATION, sys, listOf(ChatMessage("user", ConversationContext.KICKOFF)), photos)
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

    // 지금 라이징이 답할 만한 후보를 뽑는다(문맥 기반). 상대(인터뷰어)의 말 뒤에서만.
    suspend fun loadSuggestions(current: List<ChatMessage>) {
        if (current.lastOrNull()?.role != "assistant") {
            suggestions = emptyList()
            return
        }
        loadingSuggestions = true
        try {
            val dialog = current.takeLast(8).joinToString("\n") {
                (if (it.role == "assistant") "상대" else "나") + ": " + it.text
            }
            val out = AnthropicClient.complete(
                Task.SUGGEST,
                PromptLoader.replySuggester(context),
                listOf(ChatMessage("user", "다음은 지금까지의 대화야.\n\n$dialog\n\n이제 내가 답할 차례야. 내가 할 만한 답변 후보를 줘.")),
            )
            suggestions = out.lines()
                .map { line ->
                    line.trim()
                        .removePrefix("-").removePrefix("*").removePrefix("•").trim()
                        .replace(Regex("^\\d+[.)]\\s*"), "")
                        .trim().trim('"').trim('\'')
                }
                .filter { it.isNotBlank() }
                .take(3)
        } catch (_: Throwable) {
            suggestions = emptyList()
        } finally {
            loadingSuggestions = false
        }
    }

    // 상대의 새 말이 붙으면 추천을 갱신, 내 차례가 아니면 지운다.
    LaunchedEffect(turns.size, done) {
        if (!done && turns.lastOrNull()?.role == "assistant") loadSuggestions(turns) else suggestions = emptyList()
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        suggestions = emptyList()
        scope.launch {
            busy = true
            status = ""
            persist("user", text)
            turns = turns + ChatMessage("user", text)
            try {
                val apiMessages = ConversationContext.normalize(
                    listOf(ChatMessage("user", ConversationContext.KICKOFF)) + turns,
                )
                val sys = systemPrompt + "\n\n" + NowContext.build(context)
                val photos = PhotoVision.recentPhotos(context)
                val reply = AnthropicClient.complete(Task.CONVERSATION, sys, apiMessages, photos)
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
                // 오늘 사진을 삽입 후보로 넘긴다(결정 35). 권한 없으면 빈 목록.
                val photoRefs = runCatching {
                    MediaStorePhotoReader(context).readTodayPhotoRefs(Instant.now())
                }.getOrDefault(emptyList())
                val photoSection = DiaryPhotos.promptSection(photoRefs)

                val md = AnthropicClient.complete(
                    task = Task.DIARY,
                    system = dw,
                    messages = listOf(ChatMessage("user", "다음 대화를 바탕으로 오늘 일기를 써줘.\n\n$transcript$styleRef$photoSection")),
                )
                // ![](사진N) 자리표시자를 실제 사진 URI로 치환.
                DiaryFiles.save(context, today, DiaryPhotos.resolve(md, photoRefs))
                TranscriptLog.note(context, dateStr, "일기 생성됨")
                done = true
                status = "오늘 일기를 저장했어요."

                // 앱이 라이징을 더 알아가게: 이 대화에서 새로 드러난 '오래 갈 사실'만 추려 저장.
                // 일시적 감정·그날 사건은 제외. 실패해도 일기 저장에는 영향 없음.
                try {
                    val facts = AnthropicClient.complete(
                        task = Task.CONVERSATION,
                        system = "다음 대화에서 라이징에 대해 새로 알게 된, 앞으로도 오래 유지될 사실만 " +
                            "최대 3개, 각 줄 '- '로 뽑아라. 취향·습관·관계·목표처럼 지속되는 것만. " +
                            "일시적 감정이나 그날 하루 사건은 넣지 마라. 새로 알게 된 게 없으면 '없음'만 출력.",
                        messages = listOf(ChatMessage("user", transcript)),
                    )
                    PersonaMemory.append(context, facts.lines())
                } catch (_: Throwable) {
                    // 학습 실패는 조용히 무시
                }
            } catch (t: Throwable) {
                status = "일기 오류: ${t.message}"
            } finally {
                busy = false
                writing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // 상태바·내비바 아래로
            .imePadding() // 키보드가 올라오면 그만큼 바닥을 밀어 입력창을 키보드 위로
            .padding(16.dp),
    ) {
        Text("오늘", style = MaterialTheme.typography.titleMedium)
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
            Text("오늘 기분은?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            MoodPicker(selected = mood) { picked ->
                mood = picked
                DiaryFiles.setMood(context, dateStr, picked)
            }
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
            // 추천 답변 — 문맥 기반. 클릭하면 입력창에 채워지고, ↻로 다른 추천을 받는다.
            if (!busy && loadingSuggestions && suggestions.isEmpty()) {
                Text(
                    "추천 불러오는 중…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else if (!busy && suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { s ->
                        SuggestionChip(
                            onClick = { input = s },
                            label = { Text(s) },
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { loadSuggestions(turns) } },
                        enabled = !loadingSuggestions,
                    ) {
                        Text("↻", style = MaterialTheme.typography.titleLarge)
                    }
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

            TextButton(
                onClick = { writeDiary() },
                enabled = !busy && turns.size >= 2,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("오늘 하루 마무리 · 일기 쓰기") }
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
