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

        fun buildFitnessOptions(): FitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
            .build()
    }

    /**
     * Returns true if a Google account is signed in AND has Fitness permissions.
     * Falls back to just checking for any signed-in account so token acquisition
     * can still be attempted (token request itself will fail if scopes are missing).
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        // Primary check: proper Fitness permissions granted
        if (GoogleSignIn.hasPermissions(account, buildFitnessOptions())) return true
        // Fallback: account exists — try anyway; getRouteForSession will return null on auth failure
        Log.d(TAG, "Account present (${account.email}) but Fitness permissions not confirmed; will attempt anyway")
        return true
    }

    fun getSignedInEmail(): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.email
    }

    suspend fun getRouteForSession(startTimeMs: Long, endTimeMs: Long): List<RoutePoint>? =
        withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: run { Log.w(TAG, "No signed-in account"); return@withContext null }
                val androidAccount = account.account
                    ?: run { Log.w(TAG, "Account has no Android account"); return@withContext null }

                Log.d(TAG, "Fetching Fit route for session $startTimeMs-$endTimeMs as ${account.email}")

                // Obtain access token for Fitness scopes
                val token = try {
                    GoogleAuthUtil.getToken(context, androidAccount, FITNESS_SCOPE)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get Fitness token: ${e.message}")
                    return@withContext null
                }

                // Convert ms to ns for Fit REST API
                val startNs = startTimeMs * 1_000_000L
                val endNs = endTimeMs * 1_000_000L

                Log.d(TAG, "Querying Fit datasource for ns range $startNs-$endNs")

                val url = "https://www.googleapis.com/fitness/v1/users/me/dataSources/" +
                        "derived:com.google.location.sample:com.google.android.gms:" +
                        "merge_boundary_detected_location/datasets/$startNs-$endNs"

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fit API returned ${response.code} for session $startTimeMs-$endTimeMs")
                    response.close()
                    return@withContext null
                }

                val body = response.body?.string()
                response.close()

                if (body.isNullOrBlank()) {
                    Log.w(TAG, "Empty response from Fit API")
                    return@withContext null
                }

                val result = parseLocationDataset(body)
                Log.d(TAG, "Parsed ${result?.size ?: 0} route points for session $startTimeMs-$endTimeMs")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Fit route", e)
                null
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
                        // index 3 = accuracy, skip
                    }
                }

                if (lat != null && lng != null) {
                    result.add(
                        RoutePoint(
                            lat = lat,
                            lng = lng,
                            alt = alt,
                            time = java.time.Instant.ofEpochSecond(0, startTimeNs).toString()
                        )
                    )
                }
            }

            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse location dataset", e)
            null
        }
    }
}
