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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fozzels.healthexporter.model.HealthDataType
import com.fozzels.healthexporter.ui.viewmodel.HistoricalSyncViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalSyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoricalSyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    // Date picker states
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val fromPickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.startDate
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    val toPickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.endDate
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    // From date picker dialog
    if (showFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromPickerState.selectedDateMillis?.let { ms ->
                        viewModel.updateStartDate(
                            Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = fromPickerState)
        }
    }

    // To date picker dialog
    if (showToPicker) {
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toPickerState.selectedDateMillis?.let { ms ->
                        viewModel.updateEndDate(
                            Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = toPickerState)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sync Historical Data")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Date Range Card ───────────────────────────────────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Date Range",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showFromPicker = true },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isRunning
                            ) {
                                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("From: ${uiState.startDate.format(dateFmt)}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = { showToPicker = true },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isRunning
                            ) {
                                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("To: ${uiState.endDate.format(dateFmt)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        val totalDaysPreview = if (!uiState.startDate.isAfter(uiState.endDate)) {
                            java.time.temporal.ChronoUnit.DAYS.between(uiState.startDate, uiState.endDate) + 1
                        } else 0L
                        if (totalDaysPreview > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$totalDaysPreview days selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Data Types Card ───────────────────────────────────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Data Types",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row {
                                TextButton(
                                    onClick = { viewModel.selectAllTypes() },
                                    enabled = !uiState.isRunning
                                ) { Text("All", style = MaterialTheme.typography.labelSmall) }
                                TextButton(
                                    onClick = { viewModel.clearAllTypes() },
                                    enabled = !uiState.isRunning
                                ) { Text("None", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // Two-column grid of checkboxes
                        val types = HealthDataType.values().toList()
                        types.chunked(2).forEach { rowTypes ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowTypes.forEach { type ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = type in uiState.selectedTypes,
                                            onCheckedChange = { viewModel.toggleType(type) },
                                            enabled = !uiState.isRunning
                                        )
                                        Text(
                                            type.displayName,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                // Pad if odd number
                                if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ── Start / Cancel Button ─────────────────────────────────────────
            item {
                if (uiState.isRunning) {
                    OutlinedButton(
                        onClick = { viewModel.cancelSync() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel Sync")
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.startSync(uiState.startDate, uiState.endDate, uiState.selectedTypes)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.selectedTypes.isNotEmpty() &&
                                !uiState.startDate.isAfter(uiState.endDate)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Sync")
                    }
                }
            }

            // ── Progress Card (shown while running or after completion) ────────
            if (uiState.isRunning || uiState.isComplete || uiState.totalDays > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isComplete)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    if (uiState.isComplete) "Sync Complete" else "Sync Progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Overall progress bar
                            val progress = if (uiState.totalDays > 0)
                                uiState.daysProcessed.toFloat() / uiState.totalDays.toFloat()
                            else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${uiState.daysProcessed} of ${uiState.totalDays} days",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Current date being processed
                            if (uiState.currentDate.isNotBlank() && uiState.isRunning) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Processing ${uiState.currentDate}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // ETA
                            if (uiState.isRunning && uiState.estimatedTimeRemaining.isNotBlank()) {
                                Text(
                                    uiState.estimatedTimeRemaining,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Per-type status during/after sync ─────────────────────────────
            if (uiState.totalDays > 0 && uiState.selectedTypes.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                if (uiState.isComplete) "Records Exported by Type" else "Type Status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            HealthDataType.values()
                                .filter { it in uiState.selectedTypes }
                                .forEach { type ->
                                    val count = uiState.recordCounts[type.key] ?: 0
                                    TypeStatusRow(
                                        type = type,
                                        count = count,
                                        isRunning = uiState.isRunning
                                    )
                                }
                        }
                    }
                }
            }

            // ── Results Summary (complete) ────────────────────────────────────
            if (uiState.isComplete) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            SummaryRow("Days processed", "${uiState.daysProcessed}")
                            SummaryRow("Total records", "${uiState.recordCounts.values.sum()}")
                            SummaryRow("Failed days", "${uiState.failedDates.size}")
                        }
                    }
                }
            }

            // ── Failed Dates List ─────────────────────────────────────────────
            if (uiState.failedDates.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Failed Days (${uiState.failedDates.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (!uiState.isRunning) {
                                    TextButton(onClick = { viewModel.retryFailed() }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Retry")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            uiState.failedDates.take(20).forEach { date ->
                                Text(
                                    "• $date",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            if (uiState.failedDates.size > 20) {
                                Text(
                                    "... and ${uiState.failedDates.size - 20} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TypeStatusRow(type: HealthDataType, count: Int, isRunning: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(type.displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        when {
            count > 0 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            isRunning -> {
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = "In progress",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            else -> {
                Text(
                    "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
