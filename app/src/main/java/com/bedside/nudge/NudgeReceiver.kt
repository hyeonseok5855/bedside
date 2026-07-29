package com.bedside.nudge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 예약된 '틈틈이' 시각에 깨어나 생성 서비스를 띄운다(결정 39). 실제 생성·알림은
 * NudgeService가 한다 — 네트워크 호출이 BroadcastReceiver의 10초 제한을 넘길 수 있어서.
 */
class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(context, Intent(context, NudgeService::class.java))
    }
}
