package com.fozzels.healthexporter.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.fozzels.healthexporter.data.SamsungHealthManager
import com.samsung.android.sdk.health.data.permission.Permission
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Transparent bridge activity that drives the Samsung Health permission request.
 *
 * The Samsung Health SDK requires a live Activity when requesting permissions. This no-UI
 * activity is started for result, invokes the SDK suspend function, and returns the granted
 * permission set to the caller as a Parcelable ArrayList in the result Intent.
 */
@AndroidEntryPoint
class SamsungHealthPermissionsActivity : ComponentActivity() {

    @Inject lateinit var samsungHealthManager: SamsungHealthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate — store available=${samsungHealthManager.isAvailable}")

        lifecycleScope.launch {
            val granted = runCatching {
                samsungHealthManager.requestPermissionsInternal(this@SamsungHealthPermissionsActivity)
            }.onFailure { e ->
                Log.e(TAG, "requestPermissionsInternal threw", e)
            }.getOrDefault(emptySet())

            Log.d(TAG, "Permission result: ${granted.size}/${SamsungHealthManager.PERMISSIONS.size} granted")
            val resultIntent = Intent().apply {
                putParcelableArrayListExtra(EXTRA_GRANTED, ArrayList(granted))
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    companion object {
        private const val TAG = "SamsungHPermActivity"
        const val EXTRA_GRANTED = "samsung_granted_permissions"
    }
}
