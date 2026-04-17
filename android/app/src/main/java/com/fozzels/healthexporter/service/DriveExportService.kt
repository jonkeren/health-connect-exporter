package com.fozzels.healthexporter.service

import android.accounts.AccountManager
import android.content.Context
import com.fozzels.healthexporter.data.SettingsRepository
import com.fozzels.healthexporter.model.HealthExportData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    companion object {
        private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
        private const val UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    }

    suspend fun upload(
        folderId: String,
        exportDate: String,
        data: HealthExportData
    ): Result<Unit> {
        val accessToken = getAccessToken() ?: return Result.failure(
            IllegalStateException("Not signed in to Google. Please select an account via Settings.")
        )

        val fileName = "health_$exportDate.json"
        val fileContent = json.encodeToString(data)

        // Check if file already exists for this date and delete it
        deleteExistingFile(accessToken, folderId, fileName)

        // Create the file metadata
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "application/json")
            if (folderId.isNotBlank()) {
                put("parents", org.json.JSONArray().put(folderId))
            }
        }.toString()

        val boundary = "boundary_${System.currentTimeMillis()}"
        val requestBody = "--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            "$metadataJson\r\n" +
            "--$boundary\r\n" +
            "Content-Type: application/json\r\n\r\n" +
            "$fileContent\r\n" +
            "--$boundary--"

        val body = requestBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())

        val request = Request.Builder()
            .url(UPLOAD_URL)
            .post(body)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "multipart/related; boundary=$boundary")
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.close()
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                response.close()
                Result.failure(IOException("Drive upload failed ${response.code}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")
            if (accounts.isEmpty()) return@withContext null
            val settingsEmail = settingsRepository.settings.first().driveAccountEmail
            val account = if (settingsEmail.isNotBlank()) {
                accounts.firstOrNull { it.name == settingsEmail } ?: accounts[0]
            } else accounts[0]

            val future = accountManager.getAuthToken(
                account,
                DRIVE_SCOPE,
                null, null, null, null
            )
            val bundle = future.result
            bundle.getString(AccountManager.KEY_AUTHTOKEN)
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteExistingFile(accessToken: String, folderId: String, fileName: String) {
        try {
            val query = "name='$fileName' and trashed=false" +
                if (folderId.isNotBlank()) " and '$folderId' in parents" else ""
            val listRequest = Request.Builder()
                .url("$FILES_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id)")
                .get()
                .header("Authorization", "Bearer $accessToken")
                .build()

            val listResponse = okHttpClient.newCall(listRequest).execute()
            if (listResponse.isSuccessful) {
                val body = listResponse.body?.string() ?: ""
                val files = JSONObject(body).getJSONArray("files")
                for (i in 0 until files.length()) {
                    val fileId = files.getJSONObject(i).getString("id")
                    val deleteRequest = Request.Builder()
                        .url("$FILES_URL/$fileId")
                        .delete()
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                    okHttpClient.newCall(deleteRequest).execute().close()
                }
            }
            listResponse.close()
        } catch (_: Exception) { /* non-critical */ }
    }

    suspend fun listDriveFolders(): Result<List<DriveFolder>> {
        val accessToken = getAccessToken() ?: return Result.failure(
            IllegalStateException("Not signed in to Google.")
        )
        val query = "mimeType='application/vnd.google-apps.folder' and trashed=false"
        val request = Request.Builder()
            .url("$FILES_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)&pageSize=100")
            .get()
            .header("Authorization", "Bearer $accessToken")
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "{}"
                val files = JSONObject(body).getJSONArray("files")
                val folders = (0 until files.length()).map { i ->
                    val obj = files.getJSONObject(i)
                    DriveFolder(id = obj.getString("id"), name = obj.getString("name"))
                }
                response.close()
                Result.success(folders)
            } else {
                val errorBody = response.body?.string() ?: "Unknown"
                response.close()
                Result.failure(IOException("Failed to list folders: ${response.code} $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class DriveFolder(val id: String, val name: String)
