package com.fozzels.healthexporter.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fozzels.healthexporter.data.HealthConnectManager
import com.fozzels.healthexporter.ui.viewmodel.PermissionsViewModel

private val permissionLabels = mapOf(
    "android.permission.health.READ_STEPS" to "Steps",
    "android.permission.health.READ_HEART_RATE" to "Heart Rate",
    "android.permission.health.READ_SLEEP" to "Sleep",
    "android.permission.health.READ_BLOOD_PRESSURE" to "Blood Pressure",
    "android.permission.health.READ_WEIGHT" to "Weight",
    "android.permission.health.READ_TOTAL_CALORIES_BURNED" to "Calories Burned",
    "android.permission.health.READ_ACTIVE_CALORIES_BURNED" to "Active Calories",
    "android.permission.health.READ_DISTANCE" to "Distance",
    "android.permission.health.READ_OXYGEN_SATURATION" to "SpO2 / Oxygen",
    "android.permission.health.READ_BLOOD_GLUCOSE" to "Blood Glucose",
    "android.permission.health.READ_BODY_TEMPERATURE" to "Body Temperature",
    "android.permission.health.READ_HYDRATION" to "Hydration",
    "android.permission.health.READ_NUTRITION" to "Nutrition",
    "android.permission.health.READ_EXERCISE" to "Exercise Sessions"
)

@Composable
fun PermissionsScreen(viewModel: PermissionsViewModel = hiltViewModel()) {
    val grantedPermissions by viewModel.grantedPermissions.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(
        contract = viewModel.permissionsContract
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Filled.HealthAndSafety,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Health Connect Permissions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (!viewModel.isAvailable) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    "Health Connect is not available on this device. Please install it from the Play Store.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            return
        }

        Text(
            "Grant access to your health data so the app can export it daily.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val allGranted = grantedPermissions.containsAll(HealthConnectManager.PERMISSIONS)

        if (allGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "All permissions granted!",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Button(
                onClick = { permLauncher.launch(HealthConnectManager.PERMISSIONS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Grant All Health Connect Permissions")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HealthConnectManager.PERMISSIONS.sortedBy { permissionLabels[it] ?: it }) { permission ->
                val isGranted = grantedPermissions.contains(permission)
                val label = permissionLabels[permission] ?: permission.substringAfterLast(".")
                PermissionRow(label = label, isGranted = isGranted)
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, isGranted: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
