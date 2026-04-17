package com.fozzels.healthexporter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.fozzels.healthexporter.model.ExportLog
import com.fozzels.healthexporter.model.ExportStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.exportDataStore: DataStore<Preferences> by preferencesDataStore(name = "export_logs")

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val EXPORT_LOGS = stringPreferencesKey("export_logs")
        val LAST_EXPORT_ID = stringPreferencesKey("last_export_id")
    }

    val exportLogs: Flow<List<ExportLog>> = context.exportDataStore.data.map { prefs ->
        val logsJson = prefs[Keys.EXPORT_LOGS] ?: "[]"
        runCatching { json.decodeFromString<List<ExportLog>>(logsJson) }.getOrDefault(emptyList())
    }

    val lastExportLog: Flow<ExportLog?> = exportLogs.map { it.firstOrNull() }

    suspend fun addLog(log: ExportLog) {
        context.exportDataStore.edit { prefs ->
            val existing = runCatching {
                json.decodeFromString<List<ExportLog>>(prefs[Keys.EXPORT_LOGS] ?: "[]")
            }.getOrDefault(emptyList())
            val updated = (listOf(log) + existing).take(20)
            prefs[Keys.EXPORT_LOGS] = json.encodeToString(updated)
            prefs[Keys.LAST_EXPORT_ID] = log.id
        }
    }

    suspend fun createInProgressLog(date: String): ExportLog {
        val log = ExportLog(
            id = Instant.now().toEpochMilli().toString(),
            timestamp = Instant.now().toString(),
            date = date,
            status = ExportStatus.IN_PROGRESS.name,
            message = "Export in progress..."
        )
        addLog(log)
        return log
    }

    suspend fun updateLog(id: String, status: ExportStatus, message: String, recordCounts: Map<String, Int> = emptyMap()) {
        context.exportDataStore.edit { prefs ->
            val existing = runCatching {
                json.decodeFromString<List<ExportLog>>(prefs[Keys.EXPORT_LOGS] ?: "[]")
            }.getOrDefault(emptyList())
            val updated = existing.map { log ->
                if (log.id == id) log.copy(status = status.name, message = message, recordCounts = recordCounts)
                else log
            }
            prefs[Keys.EXPORT_LOGS] = json.encodeToString(updated)
        }
    }

    /**
     * Check if a day has already been exported to the HTTP server.
     * Sends a HEAD request to /api/data/{date} — returns true if the server responds 200.
     * Returns false for Drive target (DriveExportService handles deduplication internally).
     */
    suspend fun checkExistingExport(date: String, httpUrl: String, httpToken: String): Boolean {
        if (httpUrl.isBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val parsed = httpUrl.toHttpUrlOrNull() ?: return@withContext false
                val checkUrl = parsed.newBuilder()
                    .encodedPath("/api/data/$date")
                    .build()
                    .toString()
                val requestBuilder = Request.Builder().url(checkUrl).head()
                if (httpToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $httpToken")
                }
                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                val exists = response.code == 200
                response.close()
                exists
            } catch (e: Exception) {
                false
            }
        }
    }
}
