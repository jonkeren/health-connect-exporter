package com.fozzels.healthexporter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fozzels.healthexporter.model.ExportTarget
import com.fozzels.healthexporter.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Export Enable toggle
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Daily Export", fontWeight = FontWeight.Medium)
                            Text(
                                "Run automatic export every day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.settings.isExportEnabled,
                            onCheckedChange = viewModel::updateExportEnabled
                        )
                    }
                }
            }

            // Export time
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Export Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.settings.exportHour.toString().padStart(2, '0'),
                                onValueChange = { v ->
                                    v.toIntOrNull()?.let { viewModel.updateExportHour(it) }
                                },
                                label = { Text("Hour (0-23)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = uiState.settings.exportMinute.toString().padStart(2, '0'),
                                onValueChange = { v ->
                                    v.toIntOrNull()?.let { viewModel.updateExportMinute(it) }
                                },
                                label = { Text("Minute (0-59)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            "Export will run daily at ${
                                uiState.settings.exportHour.toString().padStart(2, '0')
                            }:${uiState.settings.exportMinute.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Export target selection
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Export Target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = uiState.settings.exportTarget == ExportTarget.HTTP,
                                onClick = { viewModel.updateExportTarget(ExportTarget.HTTP) }
                            )
                            Text("Custom HTTP Endpoint")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = uiState.settings.exportTarget == ExportTarget.DRIVE,
                                onClick = { viewModel.updateExportTarget(ExportTarget.DRIVE) }
                            )
                            Text("Google Drive")
                        }
                    }
                }
            }

            // HTTP settings
            if (uiState.settings.exportTarget == ExportTarget.HTTP) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("HTTP Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = uiState.settings.httpUrl,
                                onValueChange = viewModel::updateHttpUrl,
                                label = { Text("Upload URL") },
                                placeholder = { Text("https://your-server.com/api/upload") },
                                leadingIcon = { Icon(Icons.Filled.Link, null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = uiState.settings.httpToken,
                                onValueChange = viewModel::updateHttpToken,
                                label = { Text("Bearer Token (optional)") },
                                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Google Drive settings
            if (uiState.settings.exportTarget == ExportTarget.DRIVE) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Google Drive Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                            val accounts = uiState.availableAccounts
                            if (uiState.settings.driveAccountEmail.isBlank()) {
                                if (accounts.isEmpty()) {
                                    Text(
                                        "No Google accounts found on device",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text("Select Google account:", style = MaterialTheme.typography.labelMedium)
                                    accounts.forEach { email ->
                                        OutlinedButton(
                                            onClick = { viewModel.selectGoogleAccount(email) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.AccountCircle, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(email)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Signed in as", style = MaterialTheme.typography.labelSmall)
                                        Text(uiState.settings.driveAccountEmail, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    TextButton(onClick = viewModel::signOutGoogle) {
                                        Text("Sign Out")
                                    }
                                }

                                val folderLabel = if (uiState.settings.driveFolderId.isBlank())
                                    "No folder selected (root)"
                                else
                                    "Folder ID: ${uiState.settings.driveFolderId.take(20)}..."

                                OutlinedButton(
                                    onClick = { viewModel.loadDriveFolders() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Folder, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(folderLabel)
                                }
                            }
                        }
                    }
                }
            }

            // Save button
            item {
                Button(
                    onClick = viewModel::saveSettings,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Save Settings")
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // Drive folder picker dialog
    if (uiState.showFolderPicker) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFolderPicker,
            title = { Text("Select Drive Folder") },
            text = {
                if (uiState.isLoadingFolders) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.driveFolders.isEmpty()) {
                    Text("No folders found. Files will be saved to root.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        item {
                            TextButton(
                                onClick = { viewModel.selectFolder(com.fozzels.healthexporter.service.DriveFolder("", "Root (My Drive)")) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Root (My Drive)") }
                        }
                        items(uiState.driveFolders) { folder ->
                            TextButton(
                                onClick = { viewModel.selectFolder(folder) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Folder, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(folder.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissFolderPicker) { Text("Cancel") }
            }
        )
    }
}
