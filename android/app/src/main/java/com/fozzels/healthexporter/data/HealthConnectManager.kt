package com.fozzels.healthexporter.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fozzels.healthexporter.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )

        fun sleepStageToString(stage: Int): String = when (stage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
            SleepSessionRecord.STAGE_TYPE_SLEEPING -> "SLEEPING"
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
            SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
            SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
            SleepSessionRecord.STAGE_TYPE_REM -> "REM"
            else -> "UNKNOWN"
        }

        fun exerciseTypeToString(type: Int): String = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "BADMINTON"
            ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> "BASEBALL"
            ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "BASKETBALL"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "BIKING"
            ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "BOXING"
            ExerciseSessionRecord.EXERCISE_TYPE_CRICKET -> "CRICKET"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "ELLIPTICAL"
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "FOOTBALL_AMERICAN"
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> "FOOTBALL_AUSTRALIAN"
            ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC -> "FRISBEE_DISC"
            ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "GOLF"
            ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> "GUIDED_BREATHING"
            ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> "GYMNASTICS"
            ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL -> "HANDBALL"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "HIKING"
            ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY -> "ICE_HOCKEY"
            ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING -> "ICE_SKATING"
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "MARTIAL_ARTS"
            ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> "PADDLING"
            ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING -> "PARAGLIDING"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "PILATES"
            ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL -> "RACQUETBALL"
            ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "ROCK_CLIMBING"
            ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY -> "ROLLER_HOCKEY"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "ROWING"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "ROWING_MACHINE"
            ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> "RUGBY"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "RUNNING"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "RUNNING_TREADMILL"
            ExerciseSessionRecord.EXERCISE_TYPE_SAILING -> "SAILING"
            ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING -> "SCUBA_DIVING"
            ExerciseSessionRecord.EXERCISE_TYPE_SKATING -> "SKATING"
            ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "SKIING"
            ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "SNOWBOARDING"
            ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING -> "SNOWSHOEING"
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "SOCCER"
            ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL -> "SOFTBALL"
            ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> "SQUASH"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "STAIR_CLIMBING"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "STAIR_CLIMBING_MACHINE"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "STRENGTH_TRAINING"
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "STRETCHING"
            ExerciseSessionRecord.EXERCISE_TYPE_SURFING -> "SURFING"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "SWIMMING_OPEN_WATER"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "SWIMMING_POOL"
            ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "TABLE_TENNIS"
            ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "TENNIS"
            ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> "VOLLEYBALL"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "WALKING"
            ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO -> "WATER_POLO"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "WEIGHTLIFTING"
            ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR -> "WHEELCHAIR"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "YOGA"
            else -> "OTHER_$type"
        }
    }

    fun requestPermissionsActivityContract() =
        healthConnectClient.permissionController.createRequestPermissionResultContract()

    suspend fun checkPermissions(): Set<String> = withContext(Dispatchers.IO) {
        healthConnectClient.permissionController.getGrantedPermissions()
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = checkPermissions()
        return granted.containsAll(PERMISSIONS)
    }

    suspend fun readHealthData(startTime: Instant, endTime: Instant): HealthDataPayload =
        withContext(Dispatchers.IO) {
            val timeRange = TimeRangeFilter.between(startTime, endTime)
            HealthDataPayload(
                steps = readSteps(timeRange),
                heart_rate = readHeartRate(timeRange),
                sleep = readSleep(timeRange),
                blood_pressure = readBloodPressure(timeRange),
                weight = readWeight(timeRange),
                calories = readCalories(timeRange),
                active_calories = readActiveCalories(timeRange),
                distance = readDistance(timeRange),
                spo2 = readSpO2(timeRange),
                blood_glucose = readBloodGlucose(timeRange),
                body_temperature = readBodyTemperature(timeRange),
                hydration = readHydration(timeRange),
                nutrition = readNutrition(timeRange),
                exercise_sessions = readExerciseSessions(timeRange)
            )
        }

    suspend fun readHealthDataForTypes(
        startTime: Instant,
        endTime: Instant,
        selectedTypeKeys: Set<String>
    ): HealthDataPayload = withContext(Dispatchers.IO) {
        val timeRange = TimeRangeFilter.between(startTime, endTime)
        val all = selectedTypeKeys.isEmpty()
        HealthDataPayload(
            steps = if (all || "steps" in selectedTypeKeys) readSteps(timeRange) else emptyList(),
            heart_rate = if (all || "heart_rate" in selectedTypeKeys) readHeartRate(timeRange) else emptyList(),
            sleep = if (all || "sleep" in selectedTypeKeys) readSleep(timeRange) else emptyList(),
            blood_pressure = if (all || "blood_pressure" in selectedTypeKeys) readBloodPressure(timeRange) else emptyList(),
            weight = if (all || "weight" in selectedTypeKeys) readWeight(timeRange) else emptyList(),
            calories = if (all || "calories" in selectedTypeKeys) readCalories(timeRange) else emptyList(),
            active_calories = if (all || "active_calories" in selectedTypeKeys) readActiveCalories(timeRange) else emptyList(),
            distance = if (all || "distance" in selectedTypeKeys) readDistance(timeRange) else emptyList(),
            spo2 = if (all || "spo2" in selectedTypeKeys) readSpO2(timeRange) else emptyList(),
            blood_glucose = if (all || "blood_glucose" in selectedTypeKeys) readBloodGlucose(timeRange) else emptyList(),
            body_temperature = if (all || "body_temperature" in selectedTypeKeys) readBodyTemperature(timeRange) else emptyList(),
            hydration = if (all || "hydration" in selectedTypeKeys) readHydration(timeRange) else emptyList(),
            nutrition = if (all || "nutrition" in selectedTypeKeys) readNutrition(timeRange) else emptyList(),
            exercise_sessions = if (all || "exercise_sessions" in selectedTypeKeys) readExerciseSessions(timeRange) else emptyList()
        )
    }

    private suspend fun readSteps(timeRange: TimeRangeFilter): List<StepsEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(StepsRecord::class, timeRange))
            .records.map { r ->
                StepsEntry(
                    start_time = r.startTime.toString(),
                    end_time = r.endTime.toString(),
                    count = r.count,
                    source = r.metadata.dataOrigin.packageName
                )
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readHeartRate(timeRange: TimeRangeFilter): List<HeartRateEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRange))
            .records.flatMap { r ->
                r.samples.map { s -> HeartRateEntry(time = s.time.toString(), bpm = s.beatsPerMinute) }
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readSleep(timeRange: TimeRangeFilter): List<SleepEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange))
            .records.flatMap { r ->
                if (r.stages.isEmpty()) {
                    listOf(SleepEntry(start = r.startTime.toString(), end = r.endTime.toString()))
                } else {
                    r.stages.map { s ->
                        SleepEntry(
                            start = s.startTime.toString(),
                            end = s.endTime.toString(),
                            stage = sleepStageToString(s.stage)
                        )
                    }
                }
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readBloodPressure(timeRange: TimeRangeFilter): List<BloodPressureEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(BloodPressureRecord::class, timeRange))
            .records.map { r ->
                BloodPressureEntry(
                    time = r.time.toString(),
                    systolic = r.systolic.inMillimetersOfMercury,
                    diastolic = r.diastolic.inMillimetersOfMercury
                )
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readWeight(timeRange: TimeRangeFilter): List<WeightEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(WeightRecord::class, timeRange))
            .records.map { r -> WeightEntry(time = r.time.toString(), kg = r.weight.inKilograms) }
    } catch (e: Exception) { emptyList() }

    private suspend fun readCalories(timeRange: TimeRangeFilter): List<CaloriesEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRange))
            .records.map { r ->
                CaloriesEntry(
                    start_time = r.startTime.toString(),
                    end_time = r.endTime.toString(),
                    kcal = r.energy.inKilocalories
                )
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readActiveCalories(timeRange: TimeRangeFilter): List<ActiveCaloriesEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange))
            .records.map { r ->
                ActiveCaloriesEntry(
                    start_time = r.startTime.toString(),
                    end_time = r.endTime.toString(),
                    kcal = r.energy.inKilocalories
                )
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readDistance(timeRange: TimeRangeFilter): List<DistanceEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(DistanceRecord::class, timeRange))
            .records.map { r ->
                DistanceEntry(
                    start_time = r.startTime.toString(),
                    end_time = r.endTime.toString(),
                    meters = r.distance.inMeters
                )
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readSpO2(timeRange: TimeRangeFilter): List<SpO2Entry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRange))
            .records.map { r -> SpO2Entry(time = r.time.toString(), percent = r.percentage.value) }
    } catch (e: Exception) { emptyList() }

    private suspend fun readBloodGlucose(timeRange: TimeRangeFilter): List<BloodGlucoseEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class, timeRange))
            .records.map { r ->
                BloodGlucoseEntry(time = r.time.toString(), mmol_l = r.level.inMillimolesPerLiter)
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readBodyTemperature(timeRange: TimeRangeFilter): List<BodyTemperatureEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(BodyTemperatureRecord::class, timeRange))
            .records.map { r ->
                BodyTemperatureEntry(time = r.time.toString(), celsius = r.temperature.inCelsius)
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readHydration(timeRange: TimeRangeFilter): List<HydrationEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(HydrationRecord::class, timeRange))
            .records.map { r ->
                HydrationEntry(
                    start_time = r.startTime.toString(),
                    end_time = r.endTime.toString(),
                    liters = r.volume.inLiters
                )
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readNutrition(timeRange: TimeRangeFilter): List<NutritionEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(NutritionRecord::class, timeRange))
            .records.map { r ->
                NutritionEntry(
                    start_time = r.startTime.toString(),
                    end_time = r.endTime.toString(),
                    calories = r.energy?.inKilocalories,
                    protein_g = r.protein?.inGrams,
                    fat_g = r.totalFat?.inGrams,
                    carbs_g = r.totalCarbohydrate?.inGrams,
                    name = r.name
                )
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun readExerciseSessions(timeRange: TimeRangeFilter): List<ExerciseSessionEntry> = try {
        healthConnectClient.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRange))
            .records.map { r ->
                ExerciseSessionEntry(
                    start = r.startTime.toString(),
                    end = r.endTime.toString(),
                    type = exerciseTypeToString(r.exerciseType),
                    title = r.title
                )
            }
    } catch (e: Exception) { emptyList() }
}
