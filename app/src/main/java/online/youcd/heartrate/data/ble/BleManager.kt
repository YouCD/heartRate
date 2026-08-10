package online.youcd.heartrate.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val SIMULATED_ADDRESS = "00:00:00:00:00:00"
        const val SIMULATED_NAME = "模拟心率带"
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val SCAN_UPDATE_INTERVAL_MS = 500L
        private const val RSSI_STABLE_DELTA = 5
        private const val AUTO_RECONNECT_DELAY_MS = 5_000L
    }

    private val lastScanUpdate = HashMap<String, Long>()

    private var autoReconnectJob: Job? = null
    private var autoReconnectEnabled = true

    data class BleDevice(
        val name: String,
        val address: String,
        val rssi: Int
    )

    sealed interface ConnectionState {
        data object Disconnected : ConnectionState
        data object Connecting : ConnectionState
        data object Connected : ConnectionState
        data class Error(val message: String) : ConnectionState
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gattMutex = Mutex()

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDevice.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _heartRate = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 64)
    val heartRate: SharedFlow<Int> = _heartRate.asSharedFlow()

    private var scanCallback: ScanCallback? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanTimeoutJob: Job? = null
    private var simulationJob: Job? = null
    private var pendingConnectAddress: String? = null

    val isBluetoothAvailable: Boolean
        get() = adapter != null && adapter.isEnabled

    private val hasScanPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED

    private val hasConnectPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (simulationEnabled) return
        if (!isBluetoothAvailable || !hasScanPermission) {
            _connectionState.value = ConnectionState.Error("蓝牙不可用或无权限")
            return
        }
        if (_scanning.value) return
        _scannedDevices.value = emptyList()
        lastScanUpdate.clear()
        _scanning.value = true

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val address = device.address

                val now = System.currentTimeMillis()
                val last = lastScanUpdate[address]
                if (last != null && now - last < SCAN_UPDATE_INTERVAL_MS) return
                lastScanUpdate[address] = now

                val name = device.name?.takeIf { it.isNotBlank() } ?: return
                val existing = _scannedDevices.value.firstOrNull { it.address == address }
                if (existing != null &&
                    existing.name == name &&
                    kotlin.math.abs(existing.rssi - result.rssi) < RSSI_STABLE_DELTA
                ) return

                val current = _scannedDevices.value.toMutableList()
                current.removeAll { it.address == address }
                current.add(BleDevice(name, address, result.rssi))
                current.sortBy { it.name }
                _scannedDevices.value = current
            }

            override fun onScanFailed(errorCode: Int) {
                _scanning.value = false
                _connectionState.value = ConnectionState.Error("扫描失败 (code $errorCode)")
            }
        }
        scanCallback = callback

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            adapter?.bluetoothLeScanner?.startScan(null, settings, callback)
            scanTimeoutJob = scope.launch {
                delay(SCAN_TIMEOUT_MS)
                stopScan()
            }
        } catch (e: SecurityException) {
            _scanning.value = false
            _connectionState.value = ConnectionState.Error("缺少扫描权限")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_scanning.value) return
        scanTimeoutJob?.cancel()
        scanCallback?.let { callback ->
            try {
                adapter?.bluetoothLeScanner?.stopScan(callback)
            } catch (_: SecurityException) {
            }
        }
        scanCallback = null
        lastScanUpdate.clear()
        _scanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        autoReconnectJob?.cancel()
        if (simulationEnabled && address == SIMULATED_ADDRESS) {
            startSimulation()
            return
        }
        if (!hasConnectPermission) {
            _connectionState.value = ConnectionState.Error("缺少蓝牙连接权限")
            return
        }
        val device = adapter?.getRemoteDevice(address) ?: run {
            _connectionState.value = ConnectionState.Error("设备不存在")
            return
        }
        pendingConnectAddress = address
        _connectionState.value = ConnectionState.Connecting
        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private fun scheduleAutoReconnect(address: String) {
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch {
            delay(AUTO_RECONNECT_DELAY_MS)
            if (autoReconnectEnabled && pendingConnectAddress == null) {
                connect(address)
            }
        }
    }

    private fun cancelAutoReconnect() {
        autoReconnectJob?.cancel()
        autoReconnectJob = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    scope.launch {
                        gattMutex.withLock { gatt.discoverServices() }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt()
                    _connectedDevice.value = null
                    _connectedDeviceAddress.value = null
                    val addr = pendingConnectAddress
                    pendingConnectAddress = null
                    if (status != BluetoothGatt.GATT_SUCCESS && addr != null && autoReconnectEnabled) {
                        _connectionState.value = ConnectionState.Error("连接失败，正在自动重试...")
                        scheduleAutoReconnect(addr)
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGatt()
                _connectedDevice.value = null
                _connectedDeviceAddress.value = null
                val addr = pendingConnectAddress
                pendingConnectAddress = null
                if (addr != null && autoReconnectEnabled) {
                    _connectionState.value = ConnectionState.Error("服务发现失败，正在自动重试...")
                    scheduleAutoReconnect(addr)
                } else {
                    _connectionState.value = ConnectionState.Error("服务发现失败")
                }
                return
            }
            val service = gatt.getService(HEART_RATE_SERVICE)
            val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT)
            if (characteristic == null) {
                closeGatt()
                _connectedDevice.value = null
                _connectedDeviceAddress.value = null
                val addr = pendingConnectAddress
                pendingConnectAddress = null
                if (addr != null && autoReconnectEnabled) {
                    _connectionState.value = ConnectionState.Error("未找到心率服务，正在自动重试...")
                    scheduleAutoReconnect(addr)
                } else {
                    _connectionState.value = ConnectionState.Error("未找到心率服务")
                }
                return
            }
            enableHeartRateNotifications(gatt, characteristic)
            autoReconnectEnabled = true
            _connectionState.value = ConnectionState.Connected
            _connectedDevice.value = gatt.device.name ?: gatt.device.address
            _connectedDeviceAddress.value = pendingConnectAddress ?: gatt.device.address
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val bpm = HeartRateParser.parseMeasurement(value)
            if (bpm != null) {
                _heartRate.tryEmit(bpm)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enableHeartRateNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            descriptor?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
            }
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        simulationJob?.cancel()
        cancelAutoReconnect()
        autoReconnectEnabled = false
        bluetoothGatt?.disconnect()
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
        _connectedDevice.value = null
        _connectedDeviceAddress.value = null
        pendingConnectAddress = null
    }

    @SuppressLint("MissingPermission")
    fun autoConnect(address: String) {
        autoReconnectEnabled = true
        connect(address)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    // ---- 模拟模式 (DEBUG 用) ----

    var simulationEnabled: Boolean = false
        private set

    fun toggleSimulation(): Boolean {
        simulationEnabled = !simulationEnabled
        if (simulationEnabled) {
            stopScan()
        } else {
            simulationJob?.cancel()
            disconnect()
        }
        return simulationEnabled
    }

    private fun startSimulation() {
        _connectionState.value = ConnectionState.Connecting
        scope.launch {
            delay(800)
            if (simulationEnabled) {
                _connectionState.value = ConnectionState.Connected
                _connectedDevice.value = SIMULATED_NAME
                simulationJob = scope.launch {
                    var bpm = 82
                    while (simulationEnabled) {
                        _heartRate.emit(bpm)
                        bpm = (bpm + (kotlin.random.Random.nextInt(-8, 9)))
                            .coerceIn(55, 178)
                        delay(1000)
                    }
                }
            }
        }
    }
}
