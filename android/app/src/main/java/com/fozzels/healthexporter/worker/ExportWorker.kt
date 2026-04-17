package com.fozzels.healthexporter.worker

import android.app.NotificationManager
import android.content.Context
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
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter

@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthConnectManager: HealthConnectManager,
    private val settingsRepository: SettingsRepository,
    private val exportRepository: ExportRepository,
    private val httpExportService: HttpExportService,
    private val driveExportService: DriveExportService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "daily_health_export"
        const val CHANNEL_ID = "health_export_channel"
        const val NOTIFICATION_ID = 1001
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        fun scheduleDaily(context: Context, hour: Int, minute: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val now = LocalDateTime.now()
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis()

            val workRequest = OneTimeWorkRequestBuilder<ExportWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelScheduled(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!healthConnectManager.isAvailable) {
            return Result.failure(workDataOf("error" to "Health Connect not available"))
        }

        val settings = settingsRepository.settings.first()
        val exportDate = LocalDate.now().minusDays(1)
        val dateStr = exportDate.format(DATE_FMT)

        val inProgressLog = exportRepository.createInProgressLog(dateStr)

        showNotification("Exporting health data for $dateStr...", ongoing = true)

        return try {
            // Calculate time range for the export date
            val zoneId = ZoneId.systemDefault()
            val startTime = exportDate.atStartOfDay(zoneId).toInstant()
            val endTime = exportDate.plusDays(1).atStartOfDay(zoneId).toInstant()

            // Read health data
            val healthData = healthConnectManager.readHealthData(startTime, endTime)

            val exportPayload = HealthExportData(
                export_date = dateStr,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                android_version = Build.VERSION.SDK_INT,
                exported_at = java.time.Instant.now().toString(),
                data = healthData
            )

            val uploadResult = when (settings.exportTarget) {
                ExportTarget.HTTP -> {
                    if (settings.httpUrl.isBlank()) {
                        Result.failure(Exception("HTTP URL not configured"))
                    } else {
                        httpExportService.upload(settings.httpUrl, settings.httpToken.ifBlank { null }, exportPayload)
                    }
                }
                ExportTarget.DRIVE -> {
                    driveExportService.upload(settings.driveFolderId, dateStr, exportPayload)
                }
            }

            val counts = healthData.recordCounts()

            if (uploadResult.isSuccess) {
                exportRepository.updateLog(inProgressLog.id, ExportStatus.SUCCESS,
                    "Exported ${counts.values.sum()} records", counts)
                showNotification("Health export complete: ${counts.values.sum()} records for $dateStr")
                // Re-schedule for next day
                scheduleDaily(context, settings.exportHour, settings.exportMinute)
                Result.success()
            } else {
                val error = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                if (runAttemptCount < 3) {
                    exportRepository.updateLog(inProgressLog.id, ExportStatus.IN_PROGRESS,
                        "Retrying (attempt ${runAttemptCount + 1}/3): $error", counts)
                    showNotification("Export failed, retrying... (${runAttemptCount + 1}/3)")
                    Result.retry()
                } else {
                    exportRepository.updateLog(inProgressLog.id, ExportStatus.FAILURE, error, counts)
                    showNotification("Export failed: $error", isError = true)
                    scheduleDaily(context, settings.exportHour, settings.exportMinute)
                    Result.failure(workDataOf("error" to error))
                }
            }
        } catch (e: Exception) {
            val error = e.message ?: "Unknown error"
            if (runAttemptCount < 3) {
                showNotification("Export failed, retrying... (${runAttemptCount + 1}/3)")
                Result.retry()
            } else {
                exportRepository.updateLog(inProgressLog.id, ExportStatus.FAILURE, error)
                showNotification("Export failed: $error", isError = true)
                scheduleDaily(context, settings.exportHour, settings.exportMinute)
                Result.failure(workDataOf("error" to error))
            }
        }
    }

    private fun showNotification(message: String, ongoing: Boolean = false, isError: Boolean = false) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Health Connect Exporter")
            .setContentText(message)
            .setPriority(if (isError) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
