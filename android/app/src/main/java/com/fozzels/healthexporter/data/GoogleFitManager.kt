package com.fozzels.healthexporter.data

import android.content.Context
import android.util.Log
import com.fozzels.healthexporter.model.RoutePoint
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleFitManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "GoogleFitManager"
        private const val FITNESS_SCOPE =
            "oauth2:https://www.googleapis.com/auth/fitness.activity.read https://www.googleapis.com/auth/fitness.location.read"

        // Candidate datasources to try in order — Samsung Health writes to a raw source,
        // Google Fit aggregates into derived sources.
        private val LOCATION_DATASOURCES = listOf(
            // Standard derived (merge) — most common
            "derived:com.google.location.sample:com.google.android.gms:merge_boundary_detected_location",
            // Raw Samsung Health source
            "raw:com.google.location.sample:com.sec.android.app.shealth",
            // Alternative derived
            "derived:com.google.location.sample:com.google.android.gms:merge_location_samples",
            // Generic merged
            "derived:com.google.location.sample:com.google.android.gms"
        )

        fun buildFitnessOptions(): FitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
            .build()
    }

    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        if (GoogleSignIn.hasPermissions(account, buildFitnessOptions())) return true
        Log.d(TAG, "Account present (${account.email}) but Fitness permissions not confirmed; will attempt anyway")
        return true
    }

    fun getSignedInEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    suspend fun getRouteForSession(startTimeMs: Long, endTimeMs: Long): List<RoutePoint>? =
        withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: run { Log.w(TAG, "No signed-in account"); return@withContext null }
                val androidAccount = account.account
                    ?: run { Log.w(TAG, "Account has no Android account"); return@withContext null }

                Log.d(TAG, "Fetching Fit route for session $startTimeMs-$endTimeMs as ${account.email}")

                val token = try {
                    GoogleAuthUtil.getToken(context, androidAccount, FITNESS_SCOPE)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get Fitness token: ${e.message}")
                    return@withContext null
                }

                val startNs = startTimeMs * 1_000_000L
                val endNs   = endTimeMs   * 1_000_000L

                // Try each candidate datasource until we get points
                for (datasource in LOCATION_DATASOURCES) {
                    val url = "https://www.googleapis.com/fitness/v1/users/me/dataSources/" +
                            "$datasource/datasets/$startNs-$endNs"

                    Log.d(TAG, "Trying datasource: $datasource")

                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val code = response.code
                    val body = response.body?.string()
                    response.close()

                    if (code == 404) {
                        Log.d(TAG, "Datasource not found (404): $datasource")
                        continue
                    }
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Fit API returned $code for $datasource")
                        continue
                    }
                    if (body.isNullOrBlank()) continue

                    val result = parseLocationDataset(body)
                    if (!result.isNullOrEmpty()) {
                        Log.d(TAG, "Got ${result.size} route points from $datasource")
                        return@withContext result
                    }
                    Log.d(TAG, "Datasource $datasource returned 0 points")
                }

                // Nothing found — log available location datasources for debugging
                logAvailableDatasources(token, startNs, endNs)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Fit route", e)
                null
            }
        }

    /** Logs all available datasources that contain location data (for debugging empty results). */
    private fun logAvailableDatasources(token: String, startNs: Long, endNs: Long) {
        try {
            val req = Request.Builder()
                .url("https://www.googleapis.com/fitness/v1/users/me/dataSources")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string()
            resp.close()
            if (body.isNullOrBlank()) return
            val root = JSONObject(body)
            val sources = root.optJSONArray("dataSource") ?: return
            Log.d(TAG, "Available datasources (${sources.length()} total):")
            for (i in 0 until sources.length()) {
                val s = sources.getJSONObject(i)
                val id = s.optString("dataStreamId")
                val typeName = s.optJSONObject("dataType")?.optString("name") ?: ""
                if ("location" in id.lowercase() || "location" in typeName.lowercase()) {
                    Log.d(TAG, "  [LOCATION] $id  ($typeName)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list datasources: ${e.message}")
        }
    }

    private fun parseLocationDataset(json: String): List<RoutePoint>? {
        return try {
            val root = JSONObject(json)
            val points = root.optJSONArray("point") ?: return null
            val result = mutableListOf<RoutePoint>()

            for (i in 0 until points.length()) {
                val point = points.getJSONObject(i)
                val values = point.optJSONArray("value") ?: continue
                val startTimeNs = point.optString("startTimeNanos", "0").toLongOrNull() ?: 0L

                var lat: Double? = null
                var lng: Double? = null
                var alt: Double? = null

                for (j in 0 until values.length()) {
                    val v = values.getJSONObject(j)
                    when (j) {
                        0 -> lat = v.optDouble("fpVal").takeIf { !it.isNaN() }
                        1 -> lng = v.optDouble("fpVal").takeIf { !it.isNaN() }
                        2 -> alt = v.optDouble("fpVal").takeIf { !it.isNaN() }
                    }
                }

                if (lat != null && lng != null) {
                    result.add(RoutePoint(
                        lat = lat, lng = lng, alt = alt,
                        time = java.time.Instant.ofEpochSecond(0, startTimeNs).toString()
                    ))
                }
            }

            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse location dataset", e)
            null
        }
    }
}
