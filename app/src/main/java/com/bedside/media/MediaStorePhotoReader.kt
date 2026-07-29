package com.bedside.media

import android.content.ContentUris
import android.content.Context
import android.media.ExifInterface
import android.net.Uri
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
 * 오늘 촬영/추가된 사진의 개수·처음/마지막 시각과, 가능하면 GPS(EXIF)를 읽는다.
 * 이미지 자체나 파일 경로는 읽지 않는다(v1 메타데이터 최소).
 *
 * GPS는 Android 10+에서 기본 리댁션되므로 setRequireOriginal + ACCESS_MEDIA_LOCATION
 * 이 있어야 읽힌다. 권한이 없거나 EXIF가 없으면 그 사진은 위치 없음으로 센다.
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
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN, // 밀리초, 없을 수 있음(0)
                MediaStore.Images.Media.DATE_ADDED, // 초
            )
            val selection =
                "${MediaStore.Images.Media.DATE_TAKEN} >= ? OR ${MediaStore.Images.Media.DATE_ADDED} >= ?"
            val args = arrayOf(startOfDayMs.toString(), (startOfDayMs / 1000).toString())

            var count = 0
            var min = Long.MAX_VALUE
            var max = Long.MIN_VALUE
            var locatedCount = 0
            var firstLocation: LatLng? = null

            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val takenIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val takenMs = c.getLong(takenIdx)
                    val ms = if (takenMs > 0) takenMs else c.getLong(addedIdx) * 1000
                    if (ms < startOfDayMs) continue // OR 조건으로 걸린 과거분 제외
                    count++
                    if (ms < min) min = ms
                    if (ms > max) max = ms

                    val itemUri = ContentUris.withAppendedId(collection, c.getLong(idIdx))
                    readLocation(itemUri)?.let { loc ->
                        locatedCount++
                        if (firstLocation == null) firstLocation = loc
                    }
                }
            }

            if (count == 0) {
                null
            } else {
                PhotoSummary(
                    count = count,
                    first = Instant.ofEpochMilli(min),
                    last = Instant.ofEpochMilli(max),
                    locatedCount = locatedCount,
                    firstLocation = firstLocation,
                )
            }
        }

    override suspend fun readTodayPhotoRefs(
        reference: Instant,
        limit: Int,
        withLocation: Boolean,
    ): List<PhotoRef> =
        withContext(Dispatchers.IO) {
            val startOfDayMs = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
            )
            val selection =
                "${MediaStore.Images.Media.DATE_TAKEN} >= ? OR ${MediaStore.Images.Media.DATE_ADDED} >= ?"
            val args = arrayOf(startOfDayMs.toString(), (startOfDayMs / 1000).toString())

            val refs = mutableListOf<PhotoRef>()
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val takenIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val takenMs = c.getLong(takenIdx)
                    val ms = if (takenMs > 0) takenMs else c.getLong(addedIdx) * 1000
                    if (ms < startOfDayMs) continue
                    val itemUri = ContentUris.withAppendedId(collection, c.getLong(idIdx))
                    val loc = if (withLocation) readLocation(itemUri) else null
                    refs += PhotoRef(uri = itemUri.toString(), time = Instant.ofEpochMilli(ms), location = loc)
                }
            }
            refs.sortedBy { it.time }.take(limit)
        }

    private fun readLocation(itemUri: Uri): LatLng? = try {
        val readUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.setRequireOriginal(itemUri)
        } else {
            itemUri
        }
        context.contentResolver.openInputStream(readUri)?.use { stream ->
            val latLong = FloatArray(2)
            @Suppress("DEPRECATION")
            if (ExifInterface(stream).getLatLong(latLong)) {
                LatLng(latLong[0].toDouble(), latLong[1].toDouble())
            } else {
                null
            }
        }
    } catch (t: Throwable) {
        // ACCESS_MEDIA_LOCATION 없음 / EXIF 없음 / 읽기 실패 → 위치 없음으로 처리
        null
    }
}
