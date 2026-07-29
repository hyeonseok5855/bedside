package com.bedside.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bedside.data.CollectedEvent
import com.bedside.data.Db
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 지오펜스 진입·이탈 수신. 이벤트를 암호화 DB에 적립해 브리핑(질문 재료)으로 쓴다.
 * 사용자 알림은 띄우지 않는다 — 도착·이탈은 조용히 기록만 한다(결정 45).
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

        val loc = event.triggeringLocation
        Log.i(TAG, "지오펜스: $places $kind @ ${loc?.latitude},${loc?.longitude}")
        persist(context, event, kind, places, loc?.latitude, loc?.longitude)
    }

    /** 브로드캐스트가 죽기 전에 암호화 DB에 이벤트를 적립한다. */
    private fun persist(
        context: Context,
        event: GeofencingEvent,
        kind: String,
        label: String,
        lat: Double?,
        lng: Double?,
    ) {
        val now = System.currentTimeMillis()
        val occurredAt = event.triggeringLocation?.time ?: now
        val value = if (lat != null && lng != null) "$lat,$lng" else null
        val type = if (kind == "도착") "enter" else "exit"
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = Db.get(context).events()
                // 같은 장소·같은 전이가 최근 창 안에 이미 있으면 건너뛴다(경계 지터 중복 억제, 결정 46).
                val recentDup = dao.recent(30).any {
                    it.source == "geofence" && it.label == label && it.type == type &&
                        now - it.recordedAt < DEDUP_WINDOW_MS
                }
                if (!recentDup) {
                    dao.insert(
                        CollectedEvent(
                            source = "geofence",
                            type = type,
                            label = label,
                            value = value,
                            occurredAt = occurredAt,
                            recordedAt = now,
                        ),
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "이벤트 적립 실패: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "bedside"
        const val DEDUP_WINDOW_MS = 10 * 60 * 1000L // 같은 전이 10분 내 중복 억제
    }
}
