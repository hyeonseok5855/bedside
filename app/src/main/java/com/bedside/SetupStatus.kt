package com.bedside

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.bedside.health.HealthAvailability
import com.bedside.health.HealthConnectReader

/**
 * 1회성 설정이 얼마나 됐는지 계산한다. 홈의 "설정 필요" 카드가 이걸 쓴다.
 * 다 되면 [missing]이 비고, 카드가 사라진다.
 */
object SetupStatus {

    private const val PREFS = "setup"
    private const val GEOFENCES = "geofences_registered"

    fun markGeofencesRegistered(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(GEOFENCES, true).apply()
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 아직 안 된 설정 항목 라벨(사람이 읽을 말). 비어 있으면 설정 완료. */
    suspend fun missing(context: Context): List<String> {
        val out = mutableListOf<String>()

        val health = HealthConnectReader(context)
        if (health.availability() == HealthAvailability.AVAILABLE && !health.hasReadPermission()) {
            out += "건강 데이터"
        }

        val readImages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (!granted(context, readImages)) out += "사진"

        when {
            !granted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> out += "위치"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> out += "항상 위치 허용"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            out += "알림"
        }

        if (!granted(context, Manifest.permission.READ_CALENDAR)) out += "캘린더"

        if (!com.bedside.usage.ScreenTimeReader.hasPermission(context)) out += "화면 사용"

        val pm = context.getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
            out += "배터리 최적화 예외"
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(GEOFENCES, false)) out += "집·회사 지오펜스"

        return out
    }
}
