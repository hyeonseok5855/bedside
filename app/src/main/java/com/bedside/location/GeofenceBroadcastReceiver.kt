package com.bedside.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * 지오펜스 진입·이탈 수신. 지금은 로그 + 알림으로 확인만 한다.
 * (수집 DB 적립·이벤트화는 다음 단위. 지금은 "발화가 실제로 오는가" 검증용.)
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "지오펜스 오류 코드=${event.errorCode}")
            return
        }
        val kind = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "도착"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "이탈"
            else -> return
        }
        val places = event.triggeringGeofences
            ?.joinToString(", ") { GeofencePlaces.labelFor(it.requestId) }
            ?: return
        val text = "$places $kind"

        val loc = event.triggeringLocation
        Log.i(TAG, "지오펜스: $text @ ${loc?.latitude},${loc?.longitude}")
        notify(context, text)
    }

    private fun notify(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // 알림 권한 없으면 로그로만 남긴다
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "위치 이동", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("bedside")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(text.hashCode(), notification)
    }

    private companion object {
        const val TAG = "bedside"
        const val CHANNEL_ID = "geofence"
    }
}
