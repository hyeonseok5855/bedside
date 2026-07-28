package com.bedside.diary

import com.bedside.media.PhotoRef
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 일기에 장면 사진을 끼워넣는 파이프라인의 순수 로직(결정 35).
 *
 * LLM은 긴 content:// URI를 그대로 뱉으면 자주 훼손하므로, 작성기에는 번호
 * 자리표시자 `![](사진N)`만 쓰게 하고, 여기서 실제 URI로 치환한다.
 */
object DiaryPhotos {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    /** 작성기에 줄 "오늘의 사진" 안내. 사진이 없으면 빈 문자열. */
    fun promptSection(refs: List<PhotoRef>): String {
        if (refs.isEmpty()) return ""
        val lines = refs.mapIndexed { i, r -> "- 사진${i + 1} (${timeFmt.format(r.time)})" }
        return buildString {
            append("\n\n# 오늘의 사진 (선택)\n")
            append("대화에서 언급된 장면에 맞는 사진이 있으면, 그 문단 근처에 ")
            append("`![](사진N)` 형태로 넣어라(N은 아래 번호). 맞는 게 없으면 넣지 마라. ")
            append("억지로 채우지 말고, 사진 목록을 나열하지도 마라.\n")
            append(lines.joinToString("\n"))
        }
    }

    /** 작성기가 남긴 `![alt](사진N)`을 실제 URI로 치환. 범위 밖 번호는 제거한다. */
    fun resolve(markdown: String, refs: List<PhotoRef>): String {
        val re = Regex("""!\[([^\]]*)]\(사진(\d+)\)""")
        return re.replace(markdown) { m ->
            val alt = m.groupValues[1]
            val n = m.groupValues[2].toIntOrNull() ?: 0
            val ref = refs.getOrNull(n - 1)
            if (ref != null) "![$alt](${ref.uri})" else ""
        }
    }
}
