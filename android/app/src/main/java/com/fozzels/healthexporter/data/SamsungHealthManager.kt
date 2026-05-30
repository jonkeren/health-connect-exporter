package com.fozzels.healthexporter.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import com.fozzels.healthexporter.model.*
import com.fozzels.healthexporter.ui.SamsungHealthPermissionsActivity
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.data.entries.HeartRate
import com.samsung.android.sdk.health.data.data.entries.OxygenSaturation
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
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
        runCatching { HealthDataService.getStore(context) }
            .onFailure { Log.e(TAG, "Failed to get Samsung Health store", it) }
            .getOrNull()
    }

    val isAvailable: Boolean get() = store != null

    companion object {
        private const val TAG = "SamsungHealthManager"

        private val PERM_EXERCISE      = Permission.of(DataTypes.EXERCISE, AccessType.READ)
        private val PERM_SLEEP         = Permission.of(DataTypes.SLEEP, AccessType.READ)
        private val PERM_ENERGY_SCORE  = Permission.of(DataTypes.ENERGY_SCORE, AccessType.READ)
        private val PERM_STEPS         = Permission.of(DataTypes.STEPS, AccessType.READ)
        private val PERM_HEART_RATE    = Permission.of(DataTypes.HEART_RATE, AccessType.READ)
        private val PERM_BLOOD_OXYGEN  = Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ)
        private val PERM_BODY_COMP     = Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ)
        private val PERM_ACTIVITY_SUMM = Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ)

        val PERMISSIONS: Set<Permission> = setOf(
            PERM_EXERCISE, PERM_SLEEP, PERM_ENERGY_SCORE, PERM_STEPS,
            PERM_HEART_RATE, PERM_BLOOD_OXYGEN, PERM_BODY_COMP, PERM_ACTIVITY_SUMM,
        )

        /** Ordered map of every requested permission to its human-readable label. */
        val PERMISSION_LABELS: Map<Permission, String> = linkedMapOf(
            PERM_EXERCISE      to "Exercise Sessions (incl. GPS routes)",
            PERM_SLEEP         to "Sleep",
            PERM_ENERGY_SCORE  to "Energy Score",
            PERM_STEPS         to "Steps",
            PERM_HEART_RATE    to "Heart Rate",
            PERM_BLOOD_OXYGEN  to "Blood Oxygen / SpO2",
            PERM_BODY_COMP     to "Body Composition (weight, fat %, muscle, BMI)",
            PERM_ACTIVITY_SUMM to "Activity Summary",
        )
    }

    suspend fun getGrantedPermissions(): Set<Permission> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptySet()
            runCatching { s.getGrantedPermissions(PERMISSIONS) }.getOrDefault(emptySet())
        }

    fun permissionContract(): ActivityResultContract<Unit, Set<Permission>> =
        object : ActivityResultContract<Unit, Set<Permission>>() {
            override fun createIntent(context: Context, input: Unit): Intent =
                Intent(context, SamsungHealthPermissionsActivity::class.java)

            override fun parseResult(resultCode: Int, intent: Intent?): Set<Permission> {
                if (resultCode != Activity.RESULT_OK || intent == null) return emptySet()
                val list: ArrayList<Permission>? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(
                            SamsungHealthPermissionsActivity.EXTRA_GRANTED,
                            Permission::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(SamsungHealthPermissionsActivity.EXTRA_GRANTED)
                    }
                return list?.toSet() ?: emptySet()
            }
        }

    suspend fun requestPermissionsInternal(activity: Activity): Set<Permission> =
        withContext(Dispatchers.Main) {
            val s = store ?: run {
                Log.e(TAG, "requestPermissionsInternal: store is null — Samsung Health unavailable")
                return@withContext emptySet()
            }
            Log.d(TAG, "requestPermissionsInternal: calling SDK requestPermissions for ${PERMISSIONS.size} permissions")
            runCatching { s.requestPermissions(PERMISSIONS, activity) }
                .onFailure { Log.e(TAG, "SDK requestPermissions threw", it) }
                .getOrDefault(emptySet())
        }

    suspend fun readSteps(startDate: LocalDate, endDate: LocalDate): List<StepsEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                // LocalTimeFilter.of is half-open [start, end), so add one day to include endDate.
                val filter = LocalTimeFilter.of(
                    startDate.atStartOfDay(),
                    endDate.plusDays(1).atStartOfDay()
                )
                val request = DataType.StepsType.TOTAL.requestBuilder
                    .setLocalTimeFilter(filter)
                    .build()
                val response = s.aggregateData(request)
                response.dataList.mapNotNull { agg ->
                    val count = agg.value as? Long ?: return@mapNotNull null
                    StepsEntry(
                        start_time = agg.startTime.toString(),
                        end_time = agg.endTime.toString(),
                        count = count,
                        source = "com.samsung.android.apps.samsunghealth"
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HeartRateEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                val filter = InstantTimeFilter.of(startTime, endTime)
                val request = DataTypes.HEART_RATE.readDataRequestBuilder
                    .setInstantTimeFilter(filter)
                    .build()
                val response = s.readData(request)
                response.dataList.flatMap { point ->
                    @Suppress("UNCHECKED_CAST")
                    val series = point.getValue(DataType.HeartRateType.SERIES_DATA) as? List<HeartRate>
                        ?: return@flatMap emptyList<HeartRateEntry>()
                    series.map { hr ->
                        HeartRateEntry(time = hr.startTime.toString(), bpm = hr.heartRate.toLong())
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readSpO2(startTime: Instant, endTime: Instant): List<SpO2Entry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                val filter = InstantTimeFilter.of(startTime, endTime)
                val request = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                    .setInstantTimeFilter(filter)
                    .build()
                val response = s.readData(request)
                response.dataList.flatMap { point ->
                    @Suppress("UNCHECKED_CAST")
                    val series = point.getValue(DataType.BloodOxygenType.SERIES_DATA) as? List<OxygenSaturation>
                        ?: return@flatMap emptyList<SpO2Entry>()
                    series.map { sat ->
                        SpO2Entry(time = sat.startTime.toString(), percent = sat.oxygenSaturation.toDouble())
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readWeight(startTime: Instant, endTime: Instant): List<WeightEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                val filter = InstantTimeFilter.of(startTime, endTime)
                val request = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
                    .setInstantTimeFilter(filter)
                    .build()
                val response = s.readData(request)
                response.dataList.mapNotNull { point ->
                    val kg = point.getValue(DataType.BodyCompositionType.WEIGHT) as? Float
                        ?: return@mapNotNull null
                    WeightEntry(time = point.startTime.toString(), kg = kg.toDouble())
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readCalories(startDate: LocalDate, endDate: LocalDate): List<CaloriesEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                // LocalTimeFilter.of is half-open [start, end), so add one day to include endDate.
                val filter = LocalTimeFilter.of(
                    startDate.atStartOfDay(),
                    endDate.plusDays(1).atStartOfDay()
                )
                val request = DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED.requestBuilder
                    .setLocalTimeFilter(filter)
                    .build()
                val response = s.aggregateData(request)
                response.dataList.mapNotNull { agg ->
                    val kcal = agg.value as? Float ?: return@mapNotNull null
                    CaloriesEntry(
                        start_time = agg.startTime.toString(),
                        end_time = agg.endTime.toString(),
                        kcal = kcal.toDouble()
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readDistance(startDate: LocalDate, endDate: LocalDate): List<DistanceEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                // LocalTimeFilter.of is half-open [start, end), so add one day to include endDate.
                val filter = LocalTimeFilter.of(
                    startDate.atStartOfDay(),
                    endDate.plusDays(1).atStartOfDay()
                )
                val request = DataType.ActivitySummaryType.TOTAL_DISTANCE.requestBuilder
                    .setLocalTimeFilter(filter)
                    .build()
                val response = s.aggregateData(request)
                response.dataList.mapNotNull { agg ->
                    val meters = agg.value as? Float ?: return@mapNotNull null
                    DistanceEntry(
                        start_time = agg.startTime.toString(),
                        end_time = agg.endTime.toString(),
                        meters = meters.toDouble()
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun readSleep(startTime: Instant, endTime: Instant): List<SleepEntry> =
        withContext(Dispatchers.IO) {
            val s = store ?: return@withContext emptyList()
            runCatching {
                val filter = InstantTimeFilter.of(startTime, endTime)
                val request = DataTypes.SLEEP.readDataRequestBuilder
                    .setInstantTimeFilter(filter)
                    .build()
                val response = s.readData(request)
                response.dataList.flatMap { point ->
                    @Suppress("UNCHECKED_CAST")
                    val sessions = point.getValue(DataType.SleepType.SESSIONS) as? List<SleepSession>
                        ?: return@flatMap emptyList<SleepEntry>()
                    sessions.flatMap { session ->
                        if (session.stages.isNullOrEmpty()) {
                            listOf(SleepEntry(start = session.startTime.toString(), end = session.endTime.toString()))
                        } else {
                            session.stages!!.map { stage ->
                                SleepEntry(
                                    start = stage.startTime.toString(),
                                    end = stage.endTime.toString(),
                                    stage = stage.stage.name
                                )
                            }
                        }
                    }
                }.distinctBy { Triple(it.start, it.end, it.stage) }
            }.getOrDefault(emptyList())
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
                // LocalDateFilter.of is half-open [start, end), so add one day to include endDate.
                val filter = LocalDateFilter.of(startDate, endDate.plusDays(1))
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
