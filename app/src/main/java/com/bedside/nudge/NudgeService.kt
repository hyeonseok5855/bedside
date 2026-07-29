package com.bedside.nudge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bedside.ConversationActivity
import com.bedside.ai.AnthropicClient
import com.bedside.ai.ChatMessage
import com.bedside.ai.Task
import com.bedside.data.Db
import com.bedside.data.Message
import com.bedside.diary.ConversationContext
import com.bedside.diary.DiaryFiles
import com.bedside.log.TranscriptLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * '틈틈이 알림' 생성용 foreground service(결정 39). 네트워크(AI) 호출은 BroadcastReceiver
 * 의 10초 제한을 넘길 수 있어, 수집(CollectionService)처럼 짧은 FGS로 돌린다.
 * 지금 상황에 맞는 한마디를 만들어 알림으로 띄우고, 오늘 대화의 assistant 턴으로도
 * 저장한 뒤 스스로 멈춘다.
 */
class NudgeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        scope.launch {
            try {
                generate(applicationContext)
            } catch (t: Throwable) {
                // AI 생성이 실패해도(네트워크·API 등) 최소한 시간대 템플릿으로 말은 건다.
                Log.w(TAG, "틈틈이 알림 생성 실패, 템플릿으로 대체: ${t.message}")
                runCatching { notifyNudge(applicationContext, fallbackLine()) }
            } finally {
                NudgeScheduler.scheduleNext(applicationContext)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun generate(context: Context) {
        if (!NudgeScheduler.enabled(context)) return
        val today = LocalDate.now()
        val date = today.toString()

        // 오늘 일기를 이미 썼으면(하루 마무리) 더 건드리지 않는다.
        if (DiaryFiles.read(context, date).isNotBlank()) return

        val stored = Db.get(context).messages().forSession(date)
        val lastAt = stored.maxOfOrNull { it.createdAt } ?: 0L
        // 방금(90분 내) 얘기했으면 안 건다.
        if (stored.isNotEmpty() && System.currentTimeMillis() - lastAt < QUIET_AFTER_TALK_MS) return

        val system = ConversationContext.system(context, today)
        val turns = stored.map { ChatMessage(it.role, it.text) }
        val messages = ConversationContext.normalize(
            listOf(ChatMessage("user", ConversationContext.KICKOFF)) +
                turns +
                ChatMessage("user", ConversationContext.NUDGE),
        )

        val reply = AnthropicClient.complete(Task.CONVERSATION, system, messages)

        Db.get(context).messages().insert(
            Message(sessionDate = date, role = "assistant", text = reply, createdAt = System.currentTimeMillis()),
        )
        TranscriptLog.startIfNew(context, date, system)
        TranscriptLog.append(context, date, "assistant", reply)

        notifyNudge(context, reply)
    }

    /** AI 생성이 안 될 때 쓰는 시간대별 한마디. */
    private fun fallbackLine(): String = when (java.time.LocalTime.now().hour) {
        in 5..10 -> "좋은 아침이에요, 라이징. 오늘 하루 어떻게 시작했어요?"
        in 11..13 -> "점심때네요. 오전은 어땠어요?"
        in 14..17 -> "오후 잘 보내고 있어요? 지금 뭐 하는 중이에요?"
        else -> "오늘 하루 어땠어요? 얘기 나눠요."
    }

    private fun notifyNudge(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(NUDGE_CHANNEL) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(NUDGE_CHANNEL, "말 걸기", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val tap = PendingIntent.getActivity(
            context,
            8,
            Intent(context, ConversationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NUDGE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("bedside")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NUDGE_NOTIF_ID, notification)
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(FG_CHANNEL) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(FG_CHANNEL, "말 걸기 준비", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(this, FG_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("bedside")
            .setContentText("말 걸 준비 중")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FG_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FG_NOTIF_ID, notification)
        }
    }

    private companion object {
        const val TAG = "bedside"
        const val QUIET_AFTER_TALK_MS = 90 * 60 * 1000L
        const val FG_CHANNEL = "nudge-fg"
        const val NUDGE_CHANNEL = "nudge"
        const val FG_NOTIF_ID = 43
        const val NUDGE_NOTIF_ID = 4242
    }
}
