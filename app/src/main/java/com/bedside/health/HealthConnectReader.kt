package com.bedside.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Health Connect 기반 수집 리더. 여러 소스(수면·걸음)를 한 클라이언트로 읽는다.
 *
 * 읽기 자체는 소스별 인터페이스([SleepReader], [StepsReader])로 나눠 두고,
 * 공통 배선(클라이언트·가용성·권한)만 여기서 공유한다. 소스가 셋 이상으로
 * 늘면 공통부를 별도 베이스로 뽑는다.
 *
 * 권한 요청 플로우(PermissionController 계약)는 Health Connect에 특화돼 있어
 * 화면 쪽에서 [permissions]를 직접 쓰게 둔다.
 */
class HealthConnectReader(private val context: Context) : SleepReader, StepsReader, WeightReader {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val zone: ZoneId = ZoneId.systemDefault()

    /** 화면에서 권한 요청 계약에 넘길 권한 집합(수면·걸음·몸무게 읽기). */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    override fun availability(): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
            else -> HealthAvailability.NOT_SUPPORTED
        }

    override suspend fun hasReadPermission(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    override suspend fun readLastNight(reference: Instant): SleepSummary? {
        // 기준 시각에서 지난 18시간 안의 수면 세션을 모은다.
        // 하루 하나로 가정하지 않는다(낮잠·분할 수면이 있을 수 있다).
        val windowStart = reference.minus(18, ChronoUnit.HOURS)
        val sessions = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(windowStart, reference),
            )
        ).records

        if (sessions.isEmpty()) return null

        val start = sessions.minOf { it.startTime }
        val end = sessions.maxOf { it.endTime }

        val stageMinutes = linkedMapOf<String, Long>()
        var asleepMinutes = 0L
        for (session in sessions) {
            for (stage in session.stages) {
                val minutes = Duration.between(stage.startTime, stage.endTime).toMinutes()
                stageMinutes.merge(stageName(stage.stage), minutes, Long::plus)
                if (isAsleep(stage.stage)) asleepMinutes += minutes
            }
        }

        // 단계 정보가 없으면 세션 길이의 합으로 총 수면시간을 잡는다.
        val totalMinutes = if (asleepMinutes > 0) {
            asleepMinutes
        } else {
            sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        }

        return SleepSummary(
            start = start,
            end = end,
            totalMinutes = totalMinutes,
            stageMinutes = stageMinutes,
        )
    }

    override suspend fun readTodaySteps(reference: Instant): Long? {
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, reference),
            )
        )
        return result[StepsRecord.COUNT_TOTAL]
    }

    override suspend fun readLatestWeight(reference: Instant): WeightSample? {
        // 최근 1년 안에서 가장 최신 몸무게 한 건.
        val windowStart = reference.minus(365, ChronoUnit.DAYS)
        val latest = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(windowStart, reference),
                ascendingOrder = false,
                pageSize = 1,
            )
        ).records.firstOrNull() ?: return null

        return WeightSample(time = latest.time, kilograms = latest.weight.inKilograms)
    }

    private fun isAsleep(stage: Int): Boolean = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        SleepSessionRecord.STAGE_TYPE_UNKNOWN -> false
        else -> true
    }

    private fun stageName(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> "깸"
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "누워서 깸"
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "침대 밖"
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "수면"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "얕은 수면"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "깊은 수면"
        SleepSessionRecord.STAGE_TYPE_REM -> "렘"
        else -> "알 수 없음"
    }
}
