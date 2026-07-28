package com.bedside

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bedside.diary.DiaryFiles
import com.bedside.ui.Moods
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 홈. 매일 여는 화면 — 큰 "오늘 밤 이야기하기" + 지난 일기 목록.
 * 1회성 설정이 남았으면 상단에 카드로만 알리고, 나머지 뒷단은 설정 화면으로 뺐다.
 */
class MainActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)
    private var prompting = false

    private val unlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        prompting = false
        if (result.resultCode == RESULT_OK) {
            AppLock.unlocked = true
        } else {
            // 잠금 확인을 취소하면 앱을 닫는다.
            finishAffinity()
        }
    }

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
        maybeLock()
    }

    /** 앱 잠금이 켜져 있고 아직 안 풀렸으면 기기 잠금(PIN/지문)으로 확인한다. */
    private fun maybeLock() {
        if (!AppLock.enabled(this) || AppLock.unlocked || prompting) return
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) {
            // 기기에 잠금이 없으면 확인할 방법이 없다 — 통과시킨다.
            AppLock.unlocked = true
            return
        }
        val intent = km.createConfirmDeviceCredentialIntent("bedside", "기기 잠금으로 확인해요")
        if (intent != null) {
            prompting = true
            unlockLauncher.launch(intent)
        } else {
            AppLock.unlocked = true
        }
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

        // 습관 — 압박 아닌 시각적 만족. 이번 달 기록 일수 + 최근 5주 히트맵 + 돌아보기.
        if (diaries.isNotEmpty()) {
            val diaryDates = remember(diaries) { diaries.map { it.date }.toSet() }
            val monthPrefix = remember(today) { "%d-%02d".format(today.year, today.monthValue) }
            val monthCount = diaryDates.count { it.startsWith(monthPrefix) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "이번 달 ${monthCount}일 기록했어요",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { context.startActivity(Intent(context, ReviewActivity::class.java)) }) {
                        Text("돌아보기 →")
                    }
                }
                DiaryHeatmap(diaryDates, today)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("지난 일기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (diaries.isNotEmpty()) {
                TextButton(onClick = { context.startActivity(Intent(context, SearchActivity::class.java)) }) {
                    Text("검색")
                }
            }
        }

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
                        val moodEmoji = Moods.emojiFor(entry.mood)
                        Text(
                            (if (moodEmoji != null) "$moodEmoji  " else "") + label,
                            style = MaterialTheme.typography.titleSmall,
                        )
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

/** 최근 35일(5주) 히트맵. 일기를 쓴 날은 채워진 칸. 스트릭이 아니라 '쌓임'을 보여준다. */
@Composable
private fun DiaryHeatmap(diaryDates: Set<String>, today: LocalDate) {
    val days = 35
    val cells = remember(today) { (0 until days).map { today.minusDays((days - 1 - it).toLong()) } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    val filled = diaryDates.contains(d.toString())
                    val color = if (filled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}
