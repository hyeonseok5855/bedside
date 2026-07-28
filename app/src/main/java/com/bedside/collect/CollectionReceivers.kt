package com.bedside.collect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bedside.location.GeofenceManager
import com.bedside.location.GeofencePlaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 예약된 알람 → 수집 서비스 한 번 더 띄우기. */
class CollectionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CollectionScheduler.start(context)
    }
}

/**
 * 재부팅 후: 지오펜스 재등록(기기 재시작 시 소실됨) + 수집 재시작.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geofences = GeofenceManager(context)
                geofences.register(geofences.resolve(GeofencePlaces.all))
            } catch (t: Throwable) {
                Log.w("bedside", "부팅 후 지오펜스 재등록 실패: ${t.message}")
            } finally {
                pending.finish()
            }
        }

        CollectionScheduler.start(context)
    }
}
