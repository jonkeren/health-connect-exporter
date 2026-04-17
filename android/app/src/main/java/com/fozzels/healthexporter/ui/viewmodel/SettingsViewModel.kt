package com.fozzels.healthexporter.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.fozzels.healthexporter.data.SettingsRepository
import com.fozzels.healthexporter.model.ExportSettings
import com.fozzels.healthexporter.model.ExportTarget
import com.fozzels.healthexporter.service.DriveExportService
import com.fozzels.healthexporter.service.DriveFolder
import com.fozzels.healthexporter.worker.ExportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: ExportSettings = ExportSettings(),
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
    val driveFolders: List<DriveFolder> = emptyList(),
    val isLoadingFolders: Boolean = false,
    val showFolderPicker: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val driveExportService: DriveExportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveScope)
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun needsDriveSignIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return true
        return !GoogleSignIn.hasPermissions(
            account,
            Scope("https://www.googleapis.com/auth/drive.file")
        )
    }

    fun onGoogleSignInSuccess(email: String) {
        viewModelScope.launch {
            settingsRepository.updateDriveAccount(email)
            _uiState.update {
                it.copy(
                    settings = it.settings.copy(driveAccountEmail = email),
                    snackbarMessage = "Signed in as $email"
                )
            }
        }
    }

    fun onGoogleSignInFailed(error: String) {
        _uiState.update { it.copy(snackbarMessage = "Sign-in failed: $error") }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            getGoogleSignInClient().signOut()
            settingsRepository.updateDriveAccount("")
            _uiState.update {
                it.copy(
                    settings = it.settings.copy(driveAccountEmail = ""),
                    snackbarMessage = "Signed out from Google"
                )
            }
        }
    }

    fun loadDriveFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFolders = true, showFolderPicker = true) }
            val result = driveExportService.listDriveFolders()
            if (result.isSuccess) {
                _uiState.update { it.copy(driveFolders = result.getOrDefault(emptyList()), isLoadingFolders = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingFolders = false,
                        showFolderPicker = false,
                        snackbarMessage = "Failed to load folders: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun selectFolder(folder: DriveFolder) {
        viewModelScope.launch {
            settingsRepository.updateDriveFolderId(folder.id)
            _uiState.update {
                it.copy(
                    settings = it.settings.copy(driveFolderId = folder.id),
                    showFolderPicker = false,
                    snackbarMessage = "Folder selected: ${folder.name}"
                )
            }
        }
    }

    fun dismissFolderPicker() {
        _uiState.update { it.copy(showFolderPicker = false) }
    }

    fun updateExportTarget(target: ExportTarget) {
        _uiState.update { it.copy(settings = it.settings.copy(exportTarget = target)) }
    }

    fun updateHttpUrl(url: String) {
        _uiState.update { it.copy(settings = it.settings.copy(httpUrl = url)) }
    }

    fun updateHttpToken(token: String) {
        _uiState.update { it.copy(settings = it.settings.copy(httpToken = token)) }
    }

    fun updateExportHour(hour: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(exportHour = hour.coerceIn(0, 23))) }
    }

    fun updateExportMinute(minute: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(exportMinute = minute.coerceIn(0, 59))) }
    }

    fun updateExportEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(isExportEnabled = enabled)) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val settings = _uiState.value.settings
            settingsRepository.saveSettings(settings)

            if (settings.isExportEnabled) {
                ExportWorker.scheduleDaily(context, settings.exportHour, settings.exportMinute)
            } else {
                ExportWorker.cancelScheduled(context)
            }

            _uiState.update {
                it.copy(isSaving = false, snackbarMessage = "Settings saved")
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
