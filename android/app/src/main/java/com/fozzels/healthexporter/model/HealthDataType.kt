package com.fozzels.healthexporter.model

enum class HealthDataType(val displayName: String, val key: String) {
    STEPS("Steps", "steps"),
    HEART_RATE("Heart Rate", "heart_rate"),
    SLEEP("Sleep", "sleep"),
    BLOOD_PRESSURE("Blood Pressure", "blood_pressure"),
    WEIGHT("Weight", "weight"),
    CALORIES("Calories", "calories"),
    ACTIVE_CALORIES("Active Calories", "active_calories"),
    DISTANCE("Distance", "distance"),
    SPO2("SpO2", "spo2"),
    BLOOD_GLUCOSE("Blood Glucose", "blood_glucose"),
    BODY_TEMPERATURE("Body Temperature", "body_temperature"),
    HYDRATION("Hydration", "hydration"),
    NUTRITION("Nutrition", "nutrition"),
    EXERCISE_SESSIONS("Exercise Sessions", "exercise_sessions")
}
