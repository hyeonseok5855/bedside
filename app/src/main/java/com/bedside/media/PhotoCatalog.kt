package com.bedside.media

import android.content.Context
import android.location.Location
import android.net.Uri
import com.bedside.location.GeofencePlaces
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 오늘 찍은 사진의 '목록(메타데이터)'을 만든다(결정 44). AI는 이 목록(번호·시각·위치)만
 * 보고, 대화 맥락상 볼 필요가 있는 사진을 view_photos 도구로 골라 연다. 시간 순서가
 * 아니라 필요에 따라 AI가 정한다.
 */
object PhotoCatalog {

    data class Item(val index: Int, val time: Instant, val uri: String, val place: String?)

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    /** 오늘 찍은 사진 목록. 비전 꺼졌거나 권한 없으면 빈 목록. */
    suspend fun today(context: Context): List<Item> {
        if (!PhotoVision.enabled(context) || !PhotoVision.hasPermission(context)) return emptyList()
        val refs = runCatching {
            MediaStorePhotoReader(context).readTodayPhotoRefs(Instant.now(), limit = 20, withLocation = true)
        }.getOrDefault(emptyList())
        return refs.mapIndexed { i, r -> Item(index = i + 1, time = r.time, uri = r.uri, place = classify(r.location)) }
    }

    /** 시스템 프롬프트에 넣을 목록 텍스트. 비면 "". */
    fun metadataText(items: List<Item>): String {
        if (items.isEmpty()) return ""
        return buildString {
            append("# 오늘 찍은 사진 (번호·시각·위치. 필요하면 view_photos 도구로 열어 보고, 필요 없으면 안 봐도 됨)\n")
            items.forEach { item ->
                append("- 사진").append(item.index).append(": ").append(timeFmt.format(item.time))
                item.place?.let { append(", ").append(it) }
                append(" 찍음\n")
            }
        }
    }

    suspend fun encode(context: Context, item: Item): String? =
        PhotoVision.encodeUri(context, Uri.parse(item.uri))

    /** 사진 GPS를 집·회사와 비교해 라벨로. GPS 없으면 null, 집·회사 아니면 "밖". */
    private fun classify(loc: LatLng?): String? {
        if (loc == null) return null
        GeofencePlaces.all.forEach { p ->
            val lat = p.lat
            val lng = p.lng
            if (lat != null && lng != null) {
                val out = FloatArray(1)
                Location.distanceBetween(loc.lat, loc.lon, lat, lng, out)
                if (out[0] <= GeofencePlaces.RADIUS_METERS * 1.5f) return p.label
            }
        }
        return "밖"
    }
}
