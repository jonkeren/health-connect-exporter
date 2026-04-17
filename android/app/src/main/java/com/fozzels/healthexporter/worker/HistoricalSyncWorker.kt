package com.fozzels.healthexporter.worker

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fozzels.healthexporter.data.ExportRepository
import com.fozzels.healthexporter.data.HealthConnectManager
import com.fozzels.healthexporter.data.SettingsRepository
import com.fozzels.healthexporter.model.*
import com.fozzels.healthexporter.service.DriveExportService
import com.fozzels.healthexporter.service.HttpExportService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@HiltWorker
class HistoricalSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthConnectManager: HealthConnectManager,
    private val settingsRepository: SettingsRepository,
    private val exportRepository: ExportRepository,
    private val httpExportService: HttpExportService,
    private val driveExportService: DriveExportService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "historical_sync"
        const val NOTIFICATION_ID = 1002

        // Input data keys
        const val KEY_START_DATE = "start_date"
        const val KEY_END_DATE = "end_date"
        const val KEY_SELECTED_TYPES = "selected_types"

        // Progress/output data keys
        const val PROGRESS_DAYS_PROCESSED = "days_processed"
        const val PROGRESS_TOTAL_DAYS = "total_days"
        const val PROGRESS_CURRENT_DATE = "current_date"
        const val PROGRESS_RECORD_COUNTS_JSON = "record_counts_json"
        const val PROGRESS_FAILED_DATES_CSV = "failed_dates_csv"

        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val mapSerializer = MapSerializer(String.serializer(), Int.serializer())
        private val json = Json { ignoreUnknownKeys = true }

        fun start(context: Context, startDate: String, endDate: String, selectedTypes: List<String>) {
            val inputData = workDataOf(
                KEY_START_DATE to startDate,
                KEY_END_DATE to endDate,
                KEY_SELECTED_TYPES to selectedTypes.joinToString(",")
            )
            val workRequest = OneTimeWorkRequestBuilder<HistoricalSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo("Historical sync starting...")

    override suspend fun doWork(): Result {
        if (!healthConnectManager.isAvailable) {
            return Result.failure(workDataOf("error" to "Health Connect not available"))
        }

        val startDateStr = inputData.getString(KEY_START_DATE)
            ?: return Result.failure(workDataOf("error" to "Missing start date"))
        val endDateStr = inputData.getString(KEY_END_DATE)
            ?: return Result.failure(workDataOf("error" to "Missing end date"))
        val selectedTypesStr = inputData.getString(KEY_SELECTED_TYPES) ?: ""
        val selectedTypes: Set<String> = if (selectedTypesStr.isBlank())
            HealthDataType.values().map { it.key }.toSet()
        else
            selectedTypesStr.split(",").toSet()

        val settings = settingsRepository.settings.first()
        val startDate = LocalDate.parse(startDateStr, DATE_FMT)
        val endDate = LocalDate.parse(endDateStr, DATE_FMT)
        val totalDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt()

        val recordCounts = mutableMapOf<String, Int>()
        val failedDates = mutableListOf<String>()
        var daysProcessed = 0

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            if (isStopped) break

            val dateStr = currentDate.format(DATE_FMT)

            // Report progress and update foreground notification
            setProgress(buildProgressData(daysProcessed, totalDays, dateStr, recordCounts, failedDates))
            setForeground(buildForegroundInfo("Historical sync: $daysProcessed/$totalDays days — $dateStr"))

            // Skip days already exported (HTTP only — Drive deduplicates internally)
            val alreadyExists = if (settings.exportTarget == ExportTarget.HTTP) {
                exportRepository.checkExistingExport(dateStr, settings.httpUrl, settings.httpToken)
            } else {
                false
            }

            if (!alreadyExists) {
                try {
                    val zoneId = ZoneId.systemDefault()
                    val startTime = currentDate.atStartOfDay(zoneId).toInstant()
                    val endTime = currentDate.plusDays(1).atStartOfDay(zoneId).toInstant()

                    val healthData = healthConnectManager.readHealthDataForTypes(startTime, endTime, selectedTypes)

                    val exportPayload = HealthExportData(
                        export_date = dateStr,
                        device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        android_version = Build.VERSION.SDK_INT,
                        exported_at = Instant.now().toString(),
                        data = healthData
                    )

                    val uploadResult: kotlin.Result<Unit> = when (settings.exportTarget) {
                        ExportTarget.HTTP -> {
                            if (settings.httpUrl.isBlank())
                                kotlin.Result.failure(Exception("HTTP URL not configured"))
                            else
                                httpExportService.upload(
                                    settings.httpUrl,
                                    settings.httpToken.ifBlank { null },
                                    exportPayload
                                )
                        }
                        ExportTarget.DRIVE -> driveExportService.upload(
                            settings.driveFolderId, dateStr, exportPayload
                        )
                    }

                    if (uploadResult.isSuccess) {
                        healthData.recordCounts().forEach { (type, count) ->
                            recordCounts[type] = (recordCounts[type] ?: 0) + count
                        }
                    } else {
                        failedDates.add(dateStr)
                    }
                } catch (e: Exception) {
                    failedDates.add(dateStr)
                }
            }

            daysProcessed++
            currentDate = currentDate.plusDays(1)
            // Rate limit: small delay between days to avoid hammering HC or the server
            delay(100L)
        }

        // Final progress update
        setProgress(buildProgressData(daysProcessed, totalDays, "", recordCounts, failedDates))

        // Show summary notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val totalRecords = recordCounts.values.sum()
        val failedStr = if (failedDates.isNotEmpty()) ", ${failedDates.size} failed" else ""
        val completionNotification = NotificationCompat.Builder(context, ExportWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Historical sync complete")
            .setContentText("$daysProcessed days exported, $totalRecords records$failedStr")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, completionNotification)

        return Result.success(buildProgressData(daysProcessed, totalDays, "", recordCounts, failedDates))
    }

    private fun buildProgressData(
        daysProcessed: Int,
        totalDays: Int,
        currentDate: String,
        recordCounts: Map<String, Int>,
        failedDates: List<String>
    ): Data = workDataOf(
        PROGRESS_DAYS_PROCESSED to daysProcessed,
        PROGRESS_TOTAL_DAYS to totalDays,
        PROGRESS_CURRENT_DATE to currentDate,
        PROGRESS_RECORD_COUNTS_JSON to json.encodeToString(mapSerializer, recordCounts),
        PROGRESS_FAILED_DATES_CSV to failedDates.joinToString(",")
    )

    private fun buildForegroundInfo(text: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, ExportWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Historical Health Sync")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
