package com.bedside.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

/**
 * 오늘 화면 사용시간(스크린타임). UsageStatsManager로 기기에서 직접 읽는다 —
 * 외부 앱·라이브러리 불필요. 집중·컨디션의 재료다("오늘 좀 딴 데 많이 샜네?").
 * 일기에 나열하지 않고 질문 재료로만 쓴다(결정 50).
 *
 * PACKAGE_USAGE_STATS는 특수 권한이라 설정의 '사용 정보 접근'에서 사용자가 켜야 한다.
 */
object ScreenTimeReader {

    data class Summary(val totalMinutes: Long, val topApps: List<Pair<String, Long>>)

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** '사용 정보 접근' 설정 화면을 연다. */
    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** 오늘 자정~지금의 화면 사용 요약. 권한 없거나 데이터 없으면 null. */
    fun today(context: Context): Summary? {
        if (!hasPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()

        val pm = context.packageManager
        val agg = usm.queryAndAggregateUsageStats(start, end)
        val apps = agg.values.asSequence()
            .filter { it.totalTimeInForeground >= 60_000 } // 1분 이상
            .filter { it.packageName != context.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null } // 사용자 앱만(런처 있는)
            .map { labelFor(context, it.packageName) to it.totalTimeInForeground / 60_000 }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .toList()

        if (apps.isEmpty()) return null
        return Summary(totalMinutes = apps.sumOf { it.second }, topApps = apps.take(5))
    }

    /** 브리핑 한 줄. 비면 "". */
    fun briefingLine(context: Context): String {
        val s = today(context) ?: return ""
        val h = s.totalMinutes / 60
        val m = s.totalMinutes % 60
        val total = if (h > 0) "${h}시간 ${m}분" else "${m}분"
        val top = s.topApps.joinToString(", ") { "${it.first} ${it.second}분" }
        return "오늘 화면 사용(질문 재료): 총 $total · $top"
    }

    private fun labelFor(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
