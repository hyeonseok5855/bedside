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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bedside.diary.DiaryFiles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 지난 일기 본문 검색. 결과를 누르면 그 일기로. */
class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SearchScreen()
                }
            }
        }
    }
}

@Composable
private fun SearchScreen() {
    val context = LocalContext.current
    val rowFmt = remember { DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN) }

    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<DiaryFiles.Hit>>(emptyList()) }

    LaunchedEffect(query) {
        hits = if (query.isBlank()) emptyList() else DiaryFiles.search(context, query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("검색", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("일기에서 찾을 말") },
            singleLine = true,
        )

        if (query.isNotBlank() && hits.isEmpty()) {
            Text("결과가 없어요.", style = MaterialTheme.typography.bodyMedium)
        }

        hits.forEach { hit ->
            val label = runCatching { LocalDate.parse(hit.date).format(rowFmt) }.getOrDefault(hit.date)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(context, DiaryViewerActivity::class.java)
                                .putExtra(DiaryViewerActivity.EXTRA_DATE, hit.date),
                        )
                    },
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(hit.snippet, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
