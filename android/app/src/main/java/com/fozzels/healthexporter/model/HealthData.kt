package com.fozzels.healthexporter.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthExportData(
    val export_date: String,
    val device: String,
    val android_version: Int,
    val exported_at: String,
    val data: HealthDataPayload
)

@Serializable
data class HealthDataPayload(
    val steps: List<StepsEntry> = emptyList(),
    val heart_rate: List<HeartRateEntry> = emptyList(),
    val sleep: List<SleepEntry> = emptyList(),
    val blood_pressure: List<BloodPressureEntry> = emptyList(),
    val weight: List<WeightEntry> = emptyList(),
    val calories: List<CaloriesEntry> = emptyList(),
    val active_calories: List<ActiveCaloriesEntry> = emptyList(),
    val distance: List<DistanceEntry> = emptyList(),
    val spo2: List<SpO2Entry> = emptyList(),
    val blood_glucose: List<BloodGlucoseEntry> = emptyList(),
    val body_temperature: List<BodyTemperatureEntry> = emptyList(),
    val hydration: List<HydrationEntry> = emptyList(),
    val nutrition: List<NutritionEntry> = emptyList(),
    val exercise_sessions: List<ExerciseSessionEntry> = emptyList()
) {
    fun recordCounts(): Map<String, Int> = mapOf(
        "steps" to steps.size,
        "heart_rate" to heart_rate.size,
        "sleep" to sleep.size,
        "blood_pressure" to blood_pressure.size,
        "weight" to weight.size,
        "calories" to calories.size,
        "active_calories" to active_calories.size,
        "distance" to distance.size,
        "spo2" to spo2.size,
        "blood_glucose" to blood_glucose.size,
        "body_temperature" to body_temperature.size,
        "hydration" to hydration.size,
        "nutrition" to nutrition.size,
        "exercise_sessions" to exercise_sessions.size
    ).filter { it.value > 0 }
}

@Serializable
data class StepsEntry(
    val start_time: String,
    val end_time: String,
    val count: Long,
    val source: String = ""
)

@Serializable
data class HeartRateEntry(
    val time: String,
    val bpm: Long
)

@Serializable
data class SleepEntry(
    val start: String,
    val end: String,
    val stage: String = "UNKNOWN"
)

@Serializable
data class BloodPressureEntry(
    val time: String,
    val systolic: Double,
    val diastolic: Double
)

@Serializable
data class WeightEntry(
    val time: String,
    val kg: Double
)

@Serializable
data class CaloriesEntry(
    val start_time: String,
    val end_time: String,
    val kcal: Double
)

@Serializable
data class ActiveCaloriesEntry(
    val start_time: String,
    val end_time: String,
    val kcal: Double
)

@Serializable
data class DistanceEntry(
    val start_time: String,
    val end_time: String,
    val meters: Double
)

@Serializable
data class SpO2Entry(
    val time: String,
    val percent: Double
)

@Serializable
data class BloodGlucoseEntry(
    val time: String,
    val mmol_l: Double
)

@Serializable
data class BodyTemperatureEntry(
    val time: String,
    val celsius: Double
)

@Serializable
data class HydrationEntry(
    val start_time: String,
    val end_time: String,
    val liters: Double
)

@Serializable
data class NutritionEntry(
    val start_time: String,
    val end_time: String,
    val calories: Double? = null,
    val protein_g: Double? = null,
    val fat_g: Double? = null,
    val carbs_g: Double? = null,
    val name: String? = null
)

@Serializable
data class ExerciseSessionEntry(
    val start: String,
    val end: String,
    val type: String,
    val calories: Double? = null,
    val title: String? = null
)
