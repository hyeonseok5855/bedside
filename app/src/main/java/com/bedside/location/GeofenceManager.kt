package com.bedside.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import androidx.core.content.edit
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** 주소/현재위치가 좌표로 확정된 장소. */
data class ResolvedPlace(val id: String, val label: String, val lat: Double, val lng: Double)

/**
 * 집·회사 지오펜스 등록/해제. 벤더(Play Services)를 여기 가둔다.
 *
 * 좌표 해석 우선순위: 저장된 "현재 위치" 오버라이드 > personal.properties의 lat/lng
 * > 주소 Geocoder. 개인용이라 "그 장소에 서서 현재 위치로 저장"이 가장 정확하고
 * 지오코딩 실패에도 안전하다.
 */
class GeofenceManager(private val context: Context) {

    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val prefs = context.getSharedPreferences("geofence_overrides", Context.MODE_PRIVATE)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /** 지금 서 있는 위치를 해당 장소의 좌표로 저장한다(가장 정확). */
    @SuppressLint("MissingPermission")
    suspend fun captureCurrentAs(placeId: String, label: String): Result<ResolvedPlace> =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc == null) {
                        cont.resume(
                            Result.failure(IllegalStateException("현재 위치를 못 잡음(위치 서비스 켜고 재시도)")),
                        )
                    } else {
                        prefs.edit {
                            putString("$placeId.lat", loc.latitude.toString())
                            putString("$placeId.lng", loc.longitude.toString())
                        }
                        cont.resume(Result.success(ResolvedPlace(placeId, label, loc.latitude, loc.longitude)))
                    }
                }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
            cont.invokeOnCancellation { cts.cancel() }
        }

    suspend fun resolve(places: List<Place>): List<ResolvedPlace> = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.KOREA)
        places.mapNotNull { p ->
            val override = overrideFor(p.id)
            when {
                override != null -> ResolvedPlace(p.id, p.label, override.first, override.second)
                p.lat != null && p.lng != null -> ResolvedPlace(p.id, p.label, p.lat, p.lng)
                else -> try {
                    @Suppress("DEPRECATION")
                    val hit = geocoder.getFromLocationName(p.address, 1)?.firstOrNull()
                    hit?.let { ResolvedPlace(p.id, p.label, it.latitude, it.longitude) }
                } catch (t: Throwable) {
                    null
                }
            }
        }
    }

    private fun overrideFor(id: String): Pair<Double, Double>? {
        val lat = prefs.getString("$id.lat", null)?.toDoubleOrNull()
        val lng = prefs.getString("$id.lng", null)?.toDoubleOrNull()
        return if (lat != null && lng != null) lat to lng else null
    }

    @SuppressLint("MissingPermission")
    suspend fun register(resolved: List<ResolvedPlace>): Result<Int> =
        suspendCancellableCoroutine { cont ->
            if (resolved.isEmpty()) {
                cont.resume(Result.failure(IllegalStateException("확정된 장소가 없음")))
                return@suspendCancellableCoroutine
            }
            val geofences = resolved.map { rp ->
                Geofence.Builder()
                    .setRequestId(rp.id)
                    .setCircularRegion(rp.lat, rp.lng, GeofencePlaces.RADIUS_METERS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
                    )
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
            client.addGeofences(request, pendingIntent)
                .addOnSuccessListener { cont.resume(Result.success(resolved.size)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }

    suspend fun clear(): Result<Unit> = suspendCancellableCoroutine { cont ->
        client.removeGeofences(pendingIntent)
            .addOnSuccessListener { cont.resume(Result.success(Unit)) }
            .addOnFailureListener { cont.resume(Result.failure(it)) }
    }
}
