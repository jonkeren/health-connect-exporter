package com.fozzels.healthexporter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fozzels.healthexporter.model.ExportLog
import com.fozzels.healthexporter.model.ExportStatus
import com.fozzels.healthexporter.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    onNavigateToHistoricalSync: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Dashboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Export Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status cards row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "Health Connect",
                        value = if (uiState.isHealthConnectAvailable) "Available" else "Unavailable",
                        icon = Icons.Filled.HealthAndSafety,
                        isPositive = uiState.isHealthConnectAvailable,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "Permissions",
                        value = if (uiState.hasPermissions) "Granted" else "Missing",
                        icon = Icons.Filled.Security,
                        isPositive = uiState.hasPermissions,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Last export status
            item {
                LastExportCard(lastLog = uiState.lastLog)
            }

            // Manual export button
            item {
                Button(
                    onClick = { viewModel.exportNow() },
                    enabled = !uiState.exportInProgress && uiState.hasPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.exportInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Exporting...")
                    } else {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Now (Yesterday's Data)")
                    }
                }
            }

            // Historical sync card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToHistoricalSync
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sync Historical Data",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Backfill past health data for any date range",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Last export record counts
            uiState.lastLog?.let { log ->
                if (log.recordCounts.isNotEmpty()) {
                    item {
                        Text(
                            "Last Export Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(log.recordCounts.entries.toList()) { (type, count) ->
                        RecordCountRow(type = type, count = count)
                    }
                }
            }

            // Export history
            if (uiState.logs.size > 1) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Export History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(uiState.logs.drop(1).take(9)) { log ->
                    ExportLogRow(log = log)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isPositive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LastExportCard(lastLog: ExportLog?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Last Export", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            if (lastLog == null) {
                Text(
                    "No exports yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val statusColor = when (lastLog.exportStatus) {
                    ExportStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                    ExportStatus.FAILURE -> MaterialTheme.colorScheme.error
                    ExportStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
                }
                Row {
                    Text("Status: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(
                        lastLog.exportStatus.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Date: ${lastLog.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "At: ${lastLog.timestamp.take(19).replace("T", " ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastLog.message.isNotBlank()) {
                    Text(
                        lastLog.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordCountRow(type: String, count: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                type.replace("_", " ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium
            )
            Badge { Text("$count") }
        }
    }
}

@Composable
private fun ExportLogRow(log: ExportLog) {
    val statusColor = when (log.exportStatus) {
        ExportStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ExportStatus.FAILURE -> MaterialTheme.colorScheme.error
        ExportStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(log.date, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (log.message.isNotBlank()) {
                    Text(
                        log.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                log.exportStatus.name,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
