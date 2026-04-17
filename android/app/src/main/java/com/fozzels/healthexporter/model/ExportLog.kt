package com.fozzels.healthexporter.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportLog(
    val id: String,
    val timestamp: String,
    val date: String,
    val status: String, // SUCCESS, FAILURE, IN_PROGRESS
    val message: String = "",
    val recordCounts: Map<String, Int> = emptyMap()
) {
    val exportStatus: ExportStatus
        get() = ExportStatus.entries.find { it.name == status } ?: ExportStatus.FAILURE
}

enum class ExportStatus {
    SUCCESS, FAILURE, IN_PROGRESS
}
