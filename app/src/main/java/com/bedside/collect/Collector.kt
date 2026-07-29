package com.bedside.collect

import android.content.Context
import com.bedside.data.CollectedEvent
import com.bedside.data.Db
import com.bedside.health.HealthAvailability
import com.bedside.health.HealthConnectReader
import com.bedside.media.MediaStorePhotoReader
import java.time.Instant

/**
 * 한 번의 수집 스냅샷. 걸음·몸무게·사진 개수를 읽어 events 테이블에 적립한다.
 * (수면은 지난밤 기록이라 DayBriefing이 대화 시점에 직접 읽는다 — 매시간 수집하면
 *  중복이 되므로. 지오펜스는 이벤트 기반이라 여기 없다.)
 *
 * 개별 소스가 실패해도 나머지는 계속 적립한다.
 */
object Collector {

    suspend fun collectOnce(context: Context) {
        val now = System.currentTimeMillis()
        val dao = Db.get(context).events()

        val health = HealthConnectReader(context)
        // 소스별로 개별 시도한다. hasReadPermission()의 '전부 허용' 게이트를 두면
        // 몸무게 권한 하나 없다고 걸음까지 안 모으게 된다. 각 read는 권한 없으면 던지고,
        // runCatching이 그것만 건너뛴다. 결정 41.
        if (health.availability() == HealthAvailability.AVAILABLE) {
            runCatching { health.readTodaySteps(Instant.now()) }.getOrNull()?.let { steps ->
                dao.insert(
                    CollectedEvent(source = "steps", type = "snapshot", value = steps.toString(), occurredAt = now, recordedAt = now),
                )
            }
            runCatching { health.readLatestWeight(Instant.now()) }.getOrNull()?.let { w ->
                dao.insert(
                    CollectedEvent(source = "weight", type = "snapshot", value = "%.1f".format(w.kilograms), occurredAt = w.time.toEpochMilli(), recordedAt = now),
                )
            }
        }

        runCatching { MediaStorePhotoReader(context).readTodayPhotos(Instant.now()) }.getOrNull()?.let { p ->
            dao.insert(
                CollectedEvent(source = "photo", type = "snapshot", value = "${p.count}장", occurredAt = now, recordedAt = now),
            )
        }
    }
}
