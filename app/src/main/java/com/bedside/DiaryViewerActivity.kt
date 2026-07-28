package com.bedside

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bedside.diary.DiaryFiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 지난 일기 하나를 열람하고, 원하면 고친다. 수정본은 다음 일기 생성 때
 * 문체 레퍼런스로 쓰인다(결정 23).
 */
class DiaryViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date = intent.getStringExtra(EXTRA_DATE) ?: LocalDate.now().toString()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiaryViewerScreen(date)
                }
            }
        }
    }

    companion object {
        const val EXTRA_DATE = "date"
    }
}

@Composable
private fun DiaryViewerScreen(date: String) {
    val context = LocalContext.current
    val titleFmt = remember { DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN) }
    val title = remember(date) {
        runCatching { LocalDate.parse(date).format(titleFmt) }.getOrDefault(date)
    }

    var text by remember { mutableStateOf(DiaryFiles.read(context, date)) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(text) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (editing) {
                TextButton(onClick = {
                    draft = text
                    editing = false
                }) { Text("취소") }
                Button(onClick = {
                    runCatching { DiaryFiles.save(context, LocalDate.parse(date), draft) }
                    text = draft
                    editing = false
                    Toast.makeText(context, "저장했어요", Toast.LENGTH_SHORT).show()
                }) { Text("저장") }
            } else {
                TextButton(onClick = {
                    draft = text
                    editing = true
                }) { Text("수정") }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (text.isBlank()) {
            Text("내용이 없어요.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MarkdownText(text)
            }
        }
    }
}

/** 아주 가벼운 마크다운 렌더. 제목/본문/사진 placeholder만 구분한다. */
@Composable
private fun MarkdownText(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") ->
                    Text(line.removePrefix("### "), style = MaterialTheme.typography.titleSmall)
                line.startsWith("## ") ->
                    Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium)
                line.startsWith("# ") ->
                    Text(line.removePrefix("# "), style = MaterialTheme.typography.titleLarge)
                line.startsWith("![") ->
                    Text("(사진)", style = MaterialTheme.typography.bodySmall)
                line.startsWith("> ") ->
                    Text(line.removePrefix("> "), style = MaterialTheme.typography.bodyMedium)
                else -> Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
