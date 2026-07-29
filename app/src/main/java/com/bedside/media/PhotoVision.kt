package com.bedside.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * 최근에 찍은 사진을 축소 JPEG base64로 만들어 AI가 '보게' 한다(결정 43).
 * 실제 이미지가 API로 전송되므로, 최근(기본 15분) 것만·소수만·축소해서 보낸다.
 * 설정 토글로 끌 수 있고, 기본은 켜짐.
 */
object PhotoVision {

    private const val PREFS = "setup"
    private const val KEY = "photo_vision"
    private const val MAX_EDGE = 768
    private const val JPEG_QUALITY = 75

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    private fun hasPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** 최근 [withinMinutes]분 내 사진의 base64 JPEG. 최대 [limit]장. 꺼졌거나 없으면 빈 목록. */
    suspend fun recentPhotos(
        context: Context,
        withinMinutes: Long = 15,
        limit: Int = 2,
    ): List<String> = withContext(Dispatchers.IO) {
        if (!enabled(context) || !hasPermission(context)) return@withContext emptyList()
        runCatching {
            val refs = MediaStorePhotoReader(context).readTodayPhotoRefs(Instant.now(), limit = 12)
            val cutoff = Instant.now().minus(withinMinutes, ChronoUnit.MINUTES)
            refs.filter { it.time.isAfter(cutoff) }
                .takeLast(limit)
                .mapNotNull { encode(context, Uri.parse(it.uri)) }
        }.getOrDefault(emptyList())
    }

    private fun encode(context: Context, uri: Uri): String? {
        val bmp = loadDownscaled(context, uri) ?: return null
        return ByteArrayOutputStream().use { baos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun loadDownscaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (max(w, h) / sample > MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
