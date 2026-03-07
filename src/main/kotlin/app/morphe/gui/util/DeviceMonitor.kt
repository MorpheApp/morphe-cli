package app.morphe.gui.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceMonitorState(
    val devices: List<AdbDevice> = emptyList(),
    val selectedDevice: AdbDevice? = null,
    val isAdbAvailable: Boolean? = null
)

object DeviceMonitor {
    private val _state = MutableStateFlow(DeviceMonitorState())
    val state: StateFlow<DeviceMonitorState> = _state.asStateFlow()

    private val adbManager = AdbManager()
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startMonitoring() {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            // Initial ADB check
            val adbAvailable = adbManager.isAdbAvailable()
            _state.value = _state.value.copy(isAdbAvailable = adbAvailable)

            if (!adbAvailable) return@launch

            // Poll every 5 seconds
            while (isActive) {
                refreshDevices()
                delay(5000)
            }
        }
    }

    fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun selectDevice(device: AdbDevice) {
        _state.value = _state.value.copy(selectedDevice = device)
    }

    private suspend fun refreshDevices() {
        val result = adbManager.getConnectedDevices()
        result.fold(
            onSuccess = { devices ->
                val currentState = _state.value
                val readyDevices = devices.filter { it.isReady }

                // Determine selected device
                val selected = when {
                    // Keep current selection if it's still available
                    currentState.selectedDevice != null &&
                        readyDevices.any { it.id == currentState.selectedDevice.id } ->
                        readyDevices.first { it.id == currentState.selectedDevice.id }
                    // Auto-select if only one ready device
                    readyDevices.size == 1 -> readyDevices.first()
                    // Clear selection if no ready devices
                    readyDevices.isEmpty() -> null
                    // Keep null if multiple devices and no prior selection
                    else -> currentState.selectedDevice
                }

                _state.value = currentState.copy(
                    devices = devices,
                    selectedDevice = selected
                )
            },
            onFailure = {
                _state.value = _state.value.copy(
                    devices = emptyList(),
                    selectedDevice = null
                )
            }
        )
    }
}
