package com.bedside.location

import com.bedside.BuildConfig

/**
 * 지오펜스 대상 장소. 실제 주소/좌표는 personal.properties(gitignore)에서 온
 * BuildConfig 값이다. 커밋되는 소스에는 값이 없다.
 */
data class Place(
    val id: String,
    val label: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
) {
    val hasValidTarget: Boolean
        get() = address.isNotBlank() || (lat != null && lng != null)
}

object GeofencePlaces {
    const val RADIUS_METERS = 200f // 경계 지터 완화를 위해 150→200 (결정 46)

    val all: List<Place> = listOf(
        Place(
            id = "home",
            label = BuildConfig.HOME_LABEL,
            address = BuildConfig.HOME_ADDRESS,
            lat = BuildConfig.HOME_LAT.toDoubleOrNull(),
            lng = BuildConfig.HOME_LNG.toDoubleOrNull(),
        ),
        Place(
            id = "work",
            label = BuildConfig.WORK_LABEL,
            address = BuildConfig.WORK_ADDRESS,
            lat = BuildConfig.WORK_LAT.toDoubleOrNull(),
            lng = BuildConfig.WORK_LNG.toDoubleOrNull(),
        ),
    ).filter { it.hasValidTarget }

    fun labelFor(id: String): String = when (id) {
        "home" -> BuildConfig.HOME_LABEL
        "work" -> BuildConfig.WORK_LABEL
        else -> id
    }
}
