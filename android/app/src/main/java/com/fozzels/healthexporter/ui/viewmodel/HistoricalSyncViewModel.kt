package com.fozzels.healthexporter.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fozzels.healthexporter.model.HealthDataType
import com.fozzels.healthexporter.worker.HistoricalSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HistoricalSyncUiState(
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val daysProcessed: Int = 0,
    val totalDays: Int = 0,
    val currentDate: String = "",
    val recordCounts: Map<String, Int> = emptyMap(),
    val failedDates: List<String> = emptyList(),
    val estimatedTimeRemaining: String = "",
    val selectedTypes: Set<HealthDataType> = HealthDataType.values().toSet(),
    val startDate: LocalDate = LocalDate.of(2023, 1, 1),
    val endDate: LocalDate = LocalDate.now().minusDays(1),
    val snackbarMessage: String? = null
)

@HiltViewModel
class HistoricalSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private val _uiState = MutableStateFlow(HistoricalSyncUiState())
    val uiState: StateFlow<HistoricalSyncUiState> = _uiState.asStateFlow()

    private val mapSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    private var syncStartTimeMs: Long = 0L

    init {
        observeWork()
    }

    private fun observeWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(HistoricalSyncWorker.WORK_NAME)
                .collect { workInfoList ->
                    val workInfo = workInfoList.firstOrNull()
                    if (workInfo == null) {
                        _uiState.update { it.copy(isRunning = false) }
                        return@collect
                    }

                    val isRunning = workInfo.state == WorkInfo.State.RUNNING ||
                            workInfo.state == WorkInfo.State.ENQUEUED
                    val isComplete = workInfo.state == WorkInfo.State.SUCCEEDED

                    // Use output data when complete, progress data while running
                    val data = if (isComplete) workInfo.outputData else workInfo.progress

                    val daysProcessed = data.getInt(HistoricalSyncWorker.PROGRESS_DAYS_PROCESSED, 0)
                    val totalDays = data.getInt(HistoricalSyncWorker.PROGRESS_TOTAL_DAYS, 0)
                    val currentDate = data.getString(HistoricalSyncWorker.PROGRESS_CURRENT_DATE) ?: ""
                    val countsJson = data.getString(HistoricalSyncWorker.PROGRESS_RECORD_COUNTS_JSON) ?: "{}"
                    val failedCsv = data.getString(HistoricalSyncWorker.PROGRESS_FAILED_DATES_CSV) ?: ""

                    val recordCounts = runCatching {
                        json.decodeFromString(mapSerializer, countsJson)
                    }.getOrDefault(emptyMap())

                    val failedDates = if (failedCsv.isBlank()) emptyList()
                    else failedCsv.split(",").filter { it.isNotBlank() }

                    if (isRunning && syncStartTimeMs == 0L) {
                        syncStartTimeMs = System.currentTimeMillis()
                    }
                    if (!isRunning && !isComplete) {
                        syncStartTimeMs = 0L
                    }

                    val eta = computeEta(daysProcessed, totalDays)

                    _uiState.update { state ->
                        state.copy(
                            isRunning = isRunning,
                            isComplete = isComplete,
                            daysProcessed = daysProcessed,
                            totalDays = totalDays,
                            currentDate = currentDate,
                            recordCounts = recordCounts,
                            failedDates = failedDates,
                            estimatedTimeRemaining = eta
                        )
                    }
                }
        }
    }

    private fun computeEta(daysProcessed: Int, totalDays: Int): String {
        if (daysProcessed == 0 || totalDays == 0 || syncStartTimeMs == 0L) return "Calculating..."
        val elapsedMs = System.currentTimeMillis() - syncStartTimeMs
        val msPerDay = elapsedMs.toDouble() / daysProcessed
        val remainingMs = (msPerDay * (totalDays - daysProcessed)).toLong()
        val remainingSec = remainingMs / 1000
        return when {
            remainingSec <= 0 -> "Almost done"
            remainingSec < 60 -> "${remainingSec}s remaining"
            remainingSec < 3600 -> "${remainingSec / 60}m ${remainingSec % 60}s remaining"
            else -> "${remainingSec / 3600}h ${(remainingSec % 3600) / 60}m remaining"
        }
    }

    fun startSync(startDate: LocalDate, endDate: LocalDate, selectedTypes: Set<HealthDataType>) {
        if (startDate.isAfter(endDate)) {
            _uiState.update { it.copy(snackbarMessage = "Start date must be before end date") }
            return
        }
        if (selectedTypes.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Select at least one data type") }
            return
        }
        syncStartTimeMs = System.currentTimeMillis()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        HistoricalSyncWorker.start(
            context,
            startDate.format(fmt),
            endDate.format(fmt),
            selectedTypes.map { it.key }
        )
        _uiState.update {
            it.copy(
                isRunning = true,
                isComplete = false,
                daysProcessed = 0,
                totalDays = 0,
                currentDate = "",
                recordCounts = emptyMap(),
                failedDates = emptyList(),
                estimatedTimeRemaining = "Calculating..."
            )
        }
    }

    fun cancelSync() {
        HistoricalSyncWorker.cancel(context)
        syncStartTimeMs = 0L
        _uiState.update { it.copy(isRunning = false, estimatedTimeRemaining = "") }
    }

    fun retryFailed() {
        val state = _uiState.value
        if (state.failedDates.isEmpty()) return
        val sorted = state.failedDates.sorted()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        startSync(
            LocalDate.parse(sorted.first(), fmt),
            LocalDate.parse(sorted.last(), fmt),
            state.selectedTypes
        )
    }

    fun updateStartDate(date: LocalDate) {
        _uiState.update { it.copy(startDate = date) }
    }

    fun updateEndDate(date: LocalDate) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun toggleType(type: HealthDataType) {
        _uiState.update { state ->
            val updated = state.selectedTypes.toMutableSet()
            if (type in updated) updated.remove(type) else updated.add(type)
            state.copy(selectedTypes = updated)
        }
    }

    fun selectAllTypes() {
        _uiState.update { it.copy(selectedTypes = HealthDataType.values().toSet()) }
    }

    fun clearAllTypes() {
        _uiState.update { it.copy(selectedTypes = emptySet()) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
