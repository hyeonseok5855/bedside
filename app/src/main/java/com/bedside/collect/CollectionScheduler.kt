package com.bedside.collect

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 수집 주기 관리. FGS로 짧게 스냅샷하고, 다음 실행을 AlarmManager로 예약한다
 * (결정 28: Android 14 FGS 상주 제약 회피).
 */
object CollectionScheduler {

    // 주기. 야간 대화 앱이라 정밀한 간격은 필요 없다.
    private const val INTERVAL_MS = 60 * 60 * 1000L // 1시간

    /** 수집 서비스를 지금 한 번 띄운다. */
    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, CollectionService::class.java))
    }

    /** 다음 수집을 예약한다. 정확도가 중요치 않아 inexact + Doze 허용으로 둔다. */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, CollectionAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            pi,
        )
    }
}
