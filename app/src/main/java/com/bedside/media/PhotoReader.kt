package com.bedside.media

import java.time.Instant

/** 위경도 한 쌍. */
data class LatLng(val lat: Double, val lon: Double)

/**
 * 하루 사진 메타데이터 요약.
 *
 * v1은 **메타데이터만** 본다 — 시각·장수·위치. 이미지 자체나 내용(vision)은 v2.
 * 이 값도 일기에 나열되는 게 아니라 질문의 재료다("오후에 사진 몇 장 찍었네요,
 * 어디였어요?"). concept.md / egress.md 참고.
 */
data class PhotoSummary(
    val count: Int,
    val first: Instant,
    val last: Instant,
    /** GPS(EXIF)가 있는 사진 수. ACCESS_MEDIA_LOCATION 없으면 0. */
    val locatedCount: Int,
    /** 첫 지오태그 위치. 없으면 null. */
    val firstLocation: LatLng?,
)

/** 사진 메타데이터 읽기. 벤더/플랫폼을 인터페이스 뒤에 둔다. */
interface PhotoReader {
    /** 오늘(자정~기준 시각) 촬영/추가된 사진 요약. 없으면 null. */
    suspend fun readTodayPhotos(reference: Instant): PhotoSummary?
}
