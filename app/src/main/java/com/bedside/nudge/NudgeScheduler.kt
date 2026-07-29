package com.bedside.nudge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * '틈틈이 알림' 예약(결정 39). 하루 중 몇 지점에 AlarmManager로 앱을 깨워, 상황에 맞는
 * 말을 먼저 걸게 한다. 정확도가 중요치 않아 inexact(setAndAllowWhileIdle)로 둔다.
 * 한 번 울리면 다음 슬롯을 다시 예약한다.
 */
object NudgeScheduler {

    private const val PREFS = "setup"
    private const val KEY = "nudges_enabled"
    private const val REQUEST = 7

    // 깨어 있는 시간대의 몇 지점. 밤/새벽은 넣지 않는다.
    private val SLOTS = listOf(
        LocalTime.of(10, 0),
        LocalTime.of(13, 0),
        LocalTime.of(16, 30),
        LocalTime.of(21, 0),
    )

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        if (value) scheduleNext(context) else cancel(context)
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (!enabled(context)) {
            am.cancel(pendingIntent(context))
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextSlotMillis(), pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun nextSlotMillis(): Long {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val next = SLOTS.map { today.atTime(it) }.firstOrNull { it.isAfter(now) }
            ?: today.plusDays(1).atTime(SLOTS.first())
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, NudgeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
