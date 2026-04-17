package com.fozzels.healthexporter.ui.viewmodel

import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fozzels.healthexporter.data.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _grantedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissions: StateFlow<Set<String>> = _grantedPermissions.asStateFlow()

    val isAvailable: Boolean get() = healthConnectManager.isAvailable

    val permissionsContract: ActivityResultContract<Set<String>, Set<String>> =
        healthConnectManager.requestPermissionsActivityContract()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable) {
                _grantedPermissions.value = healthConnectManager.checkPermissions()
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        _grantedPermissions.value = granted
    }
}
