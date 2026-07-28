package com.bedside

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 시작된 액티비티 수를 세서 앱이 백그라운드로 내려가는 순간을 잡는다. 0이 되면
 * [AppLock.onBackground]로 잠금을 되건다. 자식 화면(설정·대화 등) 사이 이동은
 * 카운트가 0으로 떨어지지 않아 재잠금되지 않는다.
 */
class BedsideApp : Application(), Application.ActivityLifecycleCallbacks {

    private var startedCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount--
        if (startedCount <= 0) {
            startedCount = 0
            AppLock.onBackground()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
