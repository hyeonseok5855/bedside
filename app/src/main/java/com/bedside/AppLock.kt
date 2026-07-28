package com.bedside

import android.content.Context

/**
 * 앱 잠금 상태. 이 폴더엔 성적 지향·재무 등 민감 정보가 담기므로(결정 15의 '인증 없음'을
 * 보완), 원하면 기기 잠금(PIN/지문)으로 앱을 가린다. 별도 라이브러리 없이
 * KeyguardManager로 확인한다. [unlocked]는 프로세스 메모리라, 앱이 백그라운드로
 * 내려가면 [onBackground]에서 다시 false가 되어 복귀 시 재확인한다.
 */
object AppLock {
    private const val PREFS = "setup"
    private const val KEY = "app_lock"

    @Volatile
    var unlocked: Boolean = false

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    fun onBackground() {
        unlocked = false
    }
}
