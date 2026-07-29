package com.bedside.media

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 오늘 찍은 사진의 '목록(메타데이터)'을 만든다(결정 44). AI는 이 목록(번호·시각)만 보고,
 * 대화 맥락상 볼 필요가 있는 사진을 view_photos 도구로 골라 연다. 시간 순서가 아니라
 * 필요에 따라 AI가 정한다.
 */
object PhotoCatalog {

    data class Item(val index: Int, val time: Instant, val uri: String)

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    /** 오늘 찍은 사진 목록. 비전 꺼졌거나 권한 없으면 빈 목록. */
    suspend fun today(context: Context): List<Item> {
        if (!PhotoVision.enabled(context) || !PhotoVision.hasPermission(context)) return emptyList()
        val refs = runCatching {
            MediaStorePhotoReader(context).readTodayPhotoRefs(Instant.now(), limit = 20)
        }.getOrDefault(emptyList())
        return refs.mapIndexed { i, r -> Item(index = i + 1, time = r.time, uri = r.uri) }
    }

    /** 시스템 프롬프트에 넣을 목록 텍스트. 비면 "". */
    fun metadataText(items: List<Item>): String {
        if (items.isEmpty()) return ""
        return buildString {
            append("# 오늘 찍은 사진 (번호·시각만. 필요하면 view_photos 도구로 열어 보고, 필요 없으면 안 봐도 됨)\n")
            items.forEach { append("- 사진").append(it.index).append(": ").append(timeFmt.format(it.time)).append(" 찍음\n") }
        }
    }

    suspend fun encode(context: Context, item: Item): String? =
        PhotoVision.encodeUri(context, Uri.parse(item.uri))
}
