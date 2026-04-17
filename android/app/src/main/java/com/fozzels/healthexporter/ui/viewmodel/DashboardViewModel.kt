package com.fozzels.healthexporter.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fozzels.healthexporter.data.ExportRepository
import com.fozzels.healthexporter.data.HealthConnectManager
import com.fozzels.healthexporter.data.SettingsRepository
import com.fozzels.healthexporter.model.*
import com.fozzels.healthexporter.service.DriveExportService
import com.fozzels.healthexporter.service.HttpExportService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val lastLog: ExportLog? = null,
    val logs: List<ExportLog> = emptyList(),
    val isHealthConnectAvailable: Boolean = false,
    val hasPermissions: Boolean = false,
    val exportInProgress: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportRepository: ExportRepository,
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: HealthConnectManager,
    private val httpExportService: HttpExportService,
    private val driveExportService: DriveExportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isHealthConnectAvailable = healthConnectManager.isAvailable)
            }
            if (healthConnectManager.isAvailable) {
                val hasPerms = healthConnectManager.hasAllPermissions()
                _uiState.update { it.copy(hasPermissions = hasPerms) }
            }
        }

        viewModelScope.launch {
            combine(
                exportRepository.exportLogs,
                exportRepository.lastExportLog
            ) { logs, lastLog ->
                _uiState.update { it.copy(logs = logs, lastLog = lastLog) }
            }.collect()
        }
    }

    fun exportNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(exportInProgress = true, snackbarMessage = null) }

            try {
                val settings = settingsRepository.settings.first()
                val exportDate = LocalDate.now().minusDays(1)
                val dateStr = exportDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                val inProgressLog = exportRepository.createInProgressLog(dateStr)

                val zoneId = ZoneId.systemDefault()
                val startTime = exportDate.atStartOfDay(zoneId).toInstant()
                val endTime = exportDate.plusDays(1).atStartOfDay(zoneId).toInstant()

                val healthData = healthConnectManager.readHealthData(startTime, endTime)

                val exportPayload = HealthExportData(
                    export_date = dateStr,
                    device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    android_version = android.os.Build.VERSION.SDK_INT,
                    exported_at = java.time.Instant.now().toString(),
                    data = healthData
                )

                val result = when (settings.exportTarget) {
                    ExportTarget.HTTP -> {
                        if (settings.httpUrl.isBlank()) {
                            Result.failure(Exception("HTTP URL not configured. Please go to Settings."))
                        } else {
                            httpExportService.upload(
                                settings.httpUrl,
                                settings.httpToken.ifBlank { null },
                                exportPayload
                            )
                        }
                    }
                    ExportTarget.DRIVE -> {
                        driveExportService.upload(settings.driveFolderId, dateStr, exportPayload)
                    }
                }

                val counts = healthData.recordCounts()

                if (result.isSuccess) {
                    exportRepository.updateLog(
                        inProgressLog.id, ExportStatus.SUCCESS,
                        "Exported ${counts.values.sum()} records", counts
                    )
                    _uiState.update {
                        it.copy(
                            exportInProgress = false,
                            snackbarMessage = "Export successful: ${counts.values.sum()} records"
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    exportRepository.updateLog(inProgressLog.id, ExportStatus.FAILURE, error, counts)
                    _uiState.update {
                        it.copy(exportInProgress = false, snackbarMessage = "Export failed: $error")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportInProgress = false,
                        snackbarMessage = "Export error: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable) {
                val hasPerms = healthConnectManager.hasAllPermissions()
                _uiState.update { it.copy(hasPermissions = hasPerms) }
            }
        }
    }
}
