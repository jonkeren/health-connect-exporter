package com.fozzels.healthexporter.service

import com.fozzels.healthexporter.model.HealthExportData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class HttpExportService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { prettyPrint = false; encodeDefaults = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun upload(url: String, bearerToken: String?, data: HealthExportData): Result<Unit> {
        val jsonBody = json.encodeToString(data)
        val requestBody = jsonBody.toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")

        if (!bearerToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        return try {
            val response = withContext(Dispatchers.IO) { okHttpClient.newCall(requestBuilder.build()).execute() }
            if (response.isSuccessful) {
                response.close()
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                response.close()
                Result.failure(IOException("HTTP ${response.code}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
