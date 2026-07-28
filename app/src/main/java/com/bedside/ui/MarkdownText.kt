package com.bedside.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

/**
 * 아주 가벼운 마크다운 렌더. 일기·회고 열람에 공용으로 쓴다.
 * 지원: 제목(#/##/###), 불릿(- / *), 인용(> ), 사진 placeholder(![), 인라인 굵게(**).
 * 목적은 완전한 마크다운이 아니라, 이 앱이 만드는 문서를 읽기 좋게 보이는 것.
 */
@Composable
fun MarkdownText(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") ->
                    Text(inline(line.removePrefix("### ")), style = MaterialTheme.typography.titleSmall)
                line.startsWith("## ") ->
                    Text(inline(line.removePrefix("## ")), style = MaterialTheme.typography.titleMedium)
                line.startsWith("# ") ->
                    Text(inline(line.removePrefix("# ")), style = MaterialTheme.typography.titleLarge)
                line.startsWith("![") ->
                    Text("(사진)", style = MaterialTheme.typography.bodySmall)
                line.startsWith("> ") ->
                    Text(inline(line.removePrefix("> ")), style = MaterialTheme.typography.bodyMedium)
                line.startsWith("- ") || line.startsWith("* ") ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge)
                        Text(inline(line.drop(2)), style = MaterialTheme.typography.bodyLarge)
                    }
                else -> Text(inline(line), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** **굵게**만 처리하는 최소 인라인 파서. 나머지는 원문 그대로. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val open = text.indexOf("**", i)
        if (open < 0) {
            append(text.substring(i)); break
        }
        val close = text.indexOf("**", open + 2)
        if (close < 0) {
            append(text.substring(i)); break
        }
        append(text.substring(i, open))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(text.substring(open + 2, close))
        pop()
        i = close + 2
    }
}
