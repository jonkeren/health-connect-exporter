package com.fozzels.healthexporter.worker

import com.fozzels.healthexporter.model.ExerciseSessionEntry
import java.time.Instant
import kotlin.math.abs

private const val MATCH_TOLERANCE_SECONDS = 120L
private const val SUSPICIOUS_DISTANCE_THRESHOLD_METERS = 500.0

fun mergeExerciseSessions(
    shSessions: List<ExerciseSessionEntry>,
    hcSessions: List<ExerciseSessionEntry>
): List<ExerciseSessionEntry> {
    if (shSessions.isEmpty()) return hcSessions
    if (hcSessions.isEmpty()) return shSessions

    return shSessions.map { shSession ->
        if (!shSession.hasGps || shSession.route.isNullOrEmpty()) {
            val match = findMatchingSession(shSession, hcSessions)
            if (match != null) enrichFromHc(shSession, match) else shSession
        } else {
            shSession
        }
    }
}

private fun findMatchingSession(
    shSession: ExerciseSessionEntry,
    hcSessions: List<ExerciseSessionEntry>
): ExerciseSessionEntry? {
    val shStart = runCatching { Instant.parse(shSession.start) }.getOrNull() ?: return null
    return hcSessions.firstOrNull { hcSession ->
        val hcStart = runCatching { Instant.parse(hcSession.start) }.getOrNull() ?: return@firstOrNull false
        abs(hcStart.epochSecond - shStart.epochSecond) <= MATCH_TOLERANCE_SECONDS
    }
}

private fun enrichFromHc(
    shSession: ExerciseSessionEntry,
    hcSession: ExerciseSessionEntry
): ExerciseSessionEntry {
    val hasRoute = !hcSession.route.isNullOrEmpty()
    val shouldCopyDistance = hcSession.distanceMeters != null &&
        (shSession.distanceMeters == null ||
            (shSession.distanceMeters < SUSPICIOUS_DISTANCE_THRESHOLD_METERS &&
                hcSession.distanceMeters > shSession.distanceMeters))

    return shSession.copy(
        hasGps = if (hasRoute) true else shSession.hasGps,
        route = if (hasRoute) hcSession.route else shSession.route,
        distanceMeters = if (shouldCopyDistance) hcSession.distanceMeters else shSession.distanceMeters
    )
}
