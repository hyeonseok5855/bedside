package com.bedside.media

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * MediaStore 기반 [PhotoReader].
 *
 * 오늘 촬영/추가된 사진의 개수와 처음·마지막 시각만 뽑는다. 파일 경로도, GPS도
 * 아직 읽지 않는다(v1 메타데이터 최소). GPS/EXIF는 ACCESS_MEDIA_LOCATION +
 * setRequireOriginal이 필요해 다음 단위로 분리.
 */
class MediaStorePhotoReader(private val context: Context) : PhotoReader {

    private val zone: ZoneId = ZoneId.systemDefault()

    override suspend fun readTodayPhotos(reference: Instant): PhotoSummary? =
        withContext(Dispatchers.IO) {
            val startOfDayMs = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Images.Media.DATE_TAKEN, // 밀리초, 없을 수 있음(0)
                MediaStore.Images.Media.DATE_ADDED, // 초
            )
            // DATE_TAKEN(ms) 또는 DATE_ADDED(s) 중 하나라도 오늘이면 후보.
            val selection =
                "${MediaStore.Images.Media.DATE_TAKEN} >= ? OR ${MediaStore.Images.Media.DATE_ADDED} >= ?"
            val args = arrayOf(startOfDayMs.toString(), (startOfDayMs / 1000).toString())

            var count = 0
            var min = Long.MAX_VALUE
            var max = Long.MIN_VALUE

            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val takenIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val takenMs = c.getLong(takenIdx)
                    val ms = if (takenMs > 0) takenMs else c.getLong(addedIdx) * 1000
                    if (ms < startOfDayMs) continue // OR 조건으로 걸린 과거분 제외
                    count++
                    if (ms < min) min = ms
                    if (ms > max) max = ms
                }
            }

            if (count == 0) null
            else PhotoSummary(count, Instant.ofEpochMilli(min), Instant.ofEpochMilli(max))
        }
}
