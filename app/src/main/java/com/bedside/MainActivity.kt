package com.bedside

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bedside.diary.DiaryFiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 홈. 매일 여는 화면 — 큰 "오늘 밤 이야기하기" + 지난 일기 목록.
 * 1회성 설정이 남았으면 상단에 카드로만 알리고, 나머지 뒷단은 설정 화면으로 뺐다.
 */
class MainActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(resumeTick.intValue)
                }
            }
        }
    }

    // 설정·대화·일기에서 돌아오면 카드/목록을 갱신한다.
    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

@Composable
private fun HomeScreen(tick: Int) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val headerFmt = remember { DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN) }
    val rowFmt = remember { DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN) }

    var missing by remember { mutableStateOf<List<String>>(emptyList()) }
    var diaries by remember { mutableStateOf<List<DiaryFiles.Entry>>(emptyList()) }

    LaunchedEffect(tick) {
        diaries = DiaryFiles.list(context)
        missing = SetupStatus.missing(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("bedside", style = MaterialTheme.typography.headlineSmall)
                Text(today.format(headerFmt), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                Text("설정")
            }
        }

        if (missing.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("설정이 ${missing.size}개 남았어요", style = MaterialTheme.typography.titleSmall)
                    Text(missing.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    Text("탭해서 마저 설정하기 →", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Button(
            onClick = { context.startActivity(Intent(context, ConversationActivity::class.java)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
        ) {
            Text("오늘 밤 이야기하기", style = MaterialTheme.typography.titleLarge)
        }

        Text("지난 일기", style = MaterialTheme.typography.titleMedium)

        if (diaries.isEmpty()) {
            Text(
                "아직 일기가 없어요. 오늘 밤 첫 이야기를 시작해보세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            diaries.forEach { entry ->
                val label = runCatching { LocalDate.parse(entry.date).format(rowFmt) }
                    .getOrDefault(entry.date)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(context, DiaryViewerActivity::class.java)
                                    .putExtra(DiaryViewerActivity.EXTRA_DATE, entry.date),
                            )
                        },
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        if (entry.preview.isNotBlank()) {
                            Text(
                                entry.preview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
