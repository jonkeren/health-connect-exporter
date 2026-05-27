package com.fozzels.healthexporter.ui.viewmodel

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fozzels.healthexporter.data.HealthConnectManager
import com.fozzels.healthexporter.data.SamsungHealthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val samsungHealthManager: SamsungHealthManager
) : ViewModel() {

    private val _grantedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissions: StateFlow<Set<String>> = _grantedPermissions.asStateFlow()

    private val _samsungAllGranted = MutableStateFlow(false)
    val samsungAllGranted: StateFlow<Boolean> = _samsungAllGranted.asStateFlow()

    val isAvailable: Boolean get() = healthConnectManager.isAvailable
    val isSamsungAvailable: Boolean get() = samsungHealthManager.isAvailable

    val permissionsContract: ActivityResultContract<Set<String>, Set<String>> =
        healthConnectManager.requestPermissionsActivityContract()

    init {
        refreshPermissions()
        refreshSamsungPermissions()
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable) {
                _grantedPermissions.value = healthConnectManager.checkPermissions()
            }
        }
    }

    fun refreshSamsungPermissions() {
        viewModelScope.launch {
            if (samsungHealthManager.isAvailable) {
                val granted = samsungHealthManager.getGrantedPermissions()
                _samsungAllGranted.value = granted.containsAll(SamsungHealthManager.PERMISSIONS)
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        _grantedPermissions.value = granted
    }

    suspend fun requestSamsungPermissions(context: Context) {
        val granted = samsungHealthManager.requestPermissions(context)
        _samsungAllGranted.value = granted.containsAll(SamsungHealthManager.PERMISSIONS)
    }
}
