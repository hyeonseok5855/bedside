package com.bedside.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * "지금 어디" 한 줄. 마지막으로 알려진 위치를 집·회사 좌표와 비교해 분류한다.
 * 대화 컨텍스트(NowContext)에서 질문의 재료로 쓴다 — 정밀 추적이 아니라 대략의 맥락.
 */
object LocationNow {

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** "집" | "회사" | "밖 (이동 중이거나 그 외)" | null(권한 없음/위치 모름). */
    @SuppressLint("MissingPermission")
    suspend fun currentPlace(context: Context): String? {
        if (!hasPermission(context)) return null
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val loc = suspendCancellableCoroutine<Location?> { cont ->
            fused.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return null
        return classify(loc)
    }

    private fun classify(loc: Location): String {
        GeofencePlaces.all.forEach { p ->
            val lat = p.lat
            val lng = p.lng
            if (lat != null && lng != null) {
                val out = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, lat, lng, out)
                if (out[0] <= GeofencePlaces.RADIUS_METERS * 1.5f) return p.label
            }
        }
        return "밖 (이동 중이거나 그 외)"
    }
}
