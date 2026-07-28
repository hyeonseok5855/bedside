package com.bedside.media

import java.time.Instant

/**
 * 하루 사진 메타데이터 요약.
 *
 * v1은 **메타데이터만** 본다 — 시각·장수. 이미지 자체나 내용(vision)은 v2.
 * 이 값도 일기에 나열되는 게 아니라 질문의 재료다("오후에 사진 몇 장 찍었네요,
 * 뭐 찍었어요?"). concept.md / egress.md 참고.
 */
data class PhotoSummary(
    val count: Int,
    val first: Instant,
    val last: Instant,
)

/** 사진 메타데이터 읽기. 벤더/플랫폼을 인터페이스 뒤에 둔다. */
interface PhotoReader {
    /** 오늘(자정~기준 시각) 촬영/추가된 사진 요약. 없으면 null. */
    suspend fun readTodayPhotos(reference: Instant): PhotoSummary?
}
