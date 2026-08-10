package online.youcd.heartrate.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.youcd.heartrate.data.ble.BleManager
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleManager: BleManager
) : ViewModel() {

    val devices: StateFlow<List<BleManager.BleDevice>> = bleManager.scannedDevices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scanning: StateFlow<Boolean> = bleManager.scanning
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val connectionState: StateFlow<BleManager.ConnectionState> = bleManager.connectionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, BleManager.ConnectionState.Disconnected)

    fun startScan() = bleManager.startScan()

    fun stopScan() = bleManager.stopScan()

    fun restartScan() {
        bleManager.stopScan()
        bleManager.startScan()
    }

    fun connect(address: String) {
        viewModelScope.launch { bleManager.connect(address) }
    }

    fun disconnect() = bleManager.disconnect()
}
