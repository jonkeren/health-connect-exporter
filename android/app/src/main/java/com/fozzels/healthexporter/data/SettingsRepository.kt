package com.fozzels.healthexporter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.fozzels.healthexporter.model.ExportSettings
import com.fozzels.healthexporter.model.ExportTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "export_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val EXPORT_TARGET = stringPreferencesKey("export_target")
        val HTTP_URL = stringPreferencesKey("http_url")
        val HTTP_TOKEN = stringPreferencesKey("http_token")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
        val EXPORT_HOUR = intPreferencesKey("export_hour")
        val EXPORT_MINUTE = intPreferencesKey("export_minute")
        val IS_EXPORT_ENABLED = booleanPreferencesKey("is_export_enabled")
    }

    val settings: Flow<ExportSettings> = context.dataStore.data.map { prefs ->
        ExportSettings(
            exportTarget = prefs[Keys.EXPORT_TARGET]?.let {
                runCatching { ExportTarget.valueOf(it) }.getOrDefault(ExportTarget.HTTP)
            } ?: ExportTarget.HTTP,
            httpUrl = prefs[Keys.HTTP_URL] ?: "",
            httpToken = prefs[Keys.HTTP_TOKEN] ?: "",
            driveFolderId = prefs[Keys.DRIVE_FOLDER_ID] ?: "",
            driveAccountEmail = prefs[Keys.DRIVE_ACCOUNT_EMAIL] ?: "",
            exportHour = prefs[Keys.EXPORT_HOUR] ?: 2,
            exportMinute = prefs[Keys.EXPORT_MINUTE] ?: 0,
            isExportEnabled = prefs[Keys.IS_EXPORT_ENABLED] ?: true
        )
    }

    suspend fun saveSettings(settings: ExportSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EXPORT_TARGET] = settings.exportTarget.name
            prefs[Keys.HTTP_URL] = settings.httpUrl
            prefs[Keys.HTTP_TOKEN] = settings.httpToken
            prefs[Keys.DRIVE_FOLDER_ID] = settings.driveFolderId
            prefs[Keys.DRIVE_ACCOUNT_EMAIL] = settings.driveAccountEmail
            prefs[Keys.EXPORT_HOUR] = settings.exportHour
            prefs[Keys.EXPORT_MINUTE] = settings.exportMinute
            prefs[Keys.IS_EXPORT_ENABLED] = settings.isExportEnabled
        }
    }

    suspend fun updateDriveAccount(email: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DRIVE_ACCOUNT_EMAIL] = email
        }
    }

    suspend fun updateDriveFolderId(folderId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DRIVE_FOLDER_ID] = folderId
        }
    }
}
