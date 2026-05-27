package com.fozzels.healthexporter.data

import android.app.Activity
import android.content.Context
import com.fozzels.healthexporter.model.*
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamsungHealthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store by lazy {
        runCatching { HealthDataService.getStore(context) }.getOrNull()
    }

    val isAvailable: Boolean get() = store != null

    companion object {
        val PERMISSIONS: Set<Permission> = setOf(
            Permission(DataTypes.EXERCISE, AccessType.READ),
            Permission(DataTypes.SLEEP, AccessType.READ),
            Permission(DataTypes.ENERGY_SCORE, AccessType.READ)
        )
    }

    suspend fun getGrantedPermissions(): Set<Permission> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptySet()
            runCatching { s.getGrantedPermissions(PERMISSIONS) }.getOrDefault(emptySet())
        }

    suspend fun requestPermissions(activity: Activity): Set<Permission> =
        withContext(Dispatchers.Main) {
            val s = store ?: return@withContext emptySet()
            runCatching { s.requestPermissions(PERMISSIONS, activity) }.getOrDefault(emptySet())
        }

    suspend fun readExerciseSessions(startTime: Instant, endTime: Instant): List<ExerciseSessionEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                val filter = InstantTimeFilter.of(startTime, endTime)
                val request = DataTypes.EXERCISE.readDataRequestBuilder
                    .setInstantTimeFilter(filter)
                    .build()
                val response = s.readData(request)
                response.dataList.flatMap { point ->
                    @Suppress("UNCHECKED_CAST")
                    val sessions = point.getValue(DataType.ExerciseType.SESSIONS) as? List<ExerciseSession>
                        ?: return@flatMap emptyList<ExerciseSessionEntry>()
                    sessions.map { it.toEntry() }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readEnergyScores(startDate: LocalDate, endDate: LocalDate): List<EnergyScoreEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                val filter = LocalDateFilter.of(startDate, endDate)
                val request = DataTypes.ENERGY_SCORE.readDataRequestBuilder
                    .setLocalDateFilter(filter)
                    .build()
                val response = s.readData(request)
                response.dataList.mapNotNull { point ->
                    @Suppress("UNCHECKED_CAST")
                    val score = point.getValue(DataType.EnergyScoreType.ENERGY_SCORE) as? Float
                        ?: return@mapNotNull null
                    val date = point.getStartLocalDateTime().toLocalDate()
                    EnergyScoreEntry(date = date.toString(), score = score)
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readSleepScores(startTime: Instant, endTime: Instant): List<SleepScoreEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                val filter = InstantTimeFilter.of(startTime, endTime)
                val request = DataTypes.SLEEP.readDataRequestBuilder
                    .setInstantTimeFilter(filter)
                    .build()
                val response = s.readData(request)
                response.dataList.mapNotNull { point ->
                    @Suppress("UNCHECKED_CAST")
                    val score = point.getValue(DataType.SleepType.SLEEP_SCORE) as? Int
                        ?: return@mapNotNull null
                    val date = point.getStartLocalDateTime().toLocalDate()
                    SleepScoreEntry(
                        date = date.toString(),
                        score = score,
                        start = point.startTime.toString(),
                        end = point.endTime.toString()
                    )
                }
            }.getOrDefault(emptyList())
        }
}

private fun ExerciseSession.toEntry(): ExerciseSessionEntry {
    val routePoints = route?.map { loc ->
        RoutePoint(
            lat = loc.latitude.toDouble(),
            lng = loc.longitude.toDouble(),
            alt = loc.altitude?.toDouble(),
            time = loc.timestamp.toString()
        )
    }
    val typeStr = try { exerciseType?.name ?: "UNKNOWN" } catch (_: Exception) { "UNKNOWN" }
    return ExerciseSessionEntry(
        start = startTime.toString(),
        end = endTime.toString(),
        type = typeStr,
        title = customTitle,
        calories = calories?.toDouble(),
        distanceMeters = distance?.toDouble(),
        avgSpeedMs = meanSpeed?.toDouble(),
        hasGps = !routePoints.isNullOrEmpty(),
        route = routePoints,
        vo2Max = vo2Max,
        altitudeGain = altitudeGain,
        altitudeLoss = altitudeLoss,
        maxHeartRate = maxHeartRate,
        meanHeartRate = meanHeartRate,
        minHeartRate = minHeartRate,
        maxSpeed = maxSpeed
    )
}
