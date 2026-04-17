package com.fozzels.healthexporter.model

enum class ExportTarget {
    HTTP, DRIVE
}

data class ExportSettings(
    val exportTarget: ExportTarget = ExportTarget.HTTP,
    val httpUrl: String = "",
    val httpToken: String = "",
    val driveFolderId: String = "",
    val driveAccountEmail: String = "",
    val exportHour: Int = 2,
    val exportMinute: Int = 0,
    val isExportEnabled: Boolean = true
)
