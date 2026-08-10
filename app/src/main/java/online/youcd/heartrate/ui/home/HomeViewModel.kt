package online.youcd.heartrate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import online.youcd.heartrate.data.ble.BleManager
import online.youcd.heartrate.data.ble.BleManager.ConnectionState
import online.youcd.heartrate.data.ble.FloatingWindowManager
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.data.model.UserProfile
import online.youcd.heartrate.data.repository.HeartRateRepository
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val floatingWindowManager: FloatingWindowManager,
    private val repository: HeartRateRepository
) : ViewModel() {

    enum class SessionState { IDLE, RUNNING, PAUSED }

    data class SessionStats(
        val current: Int = 0,
        val min: Int = 0,
        val avg: Int = 0,
        val max: Int = 0,
        val calories: Int = 0,
        val zoneSeconds: List<Int> = List(HeartRateZone.ZONES.size) { 0 }
    )

    private val METS_BY_ZONE = mapOf(
        1 to 1.2, 2 to 3.0, 3 to 5.0, 4 to 7.0, 5 to 9.5
    )

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val connectedDeviceName: StateFlow<String?> = bleManager.connectedDeviceName

    private val _simulationEnabled = MutableStateFlow(false)
    val simulationEnabled: StateFlow<Boolean> = _simulationEnabled.asStateFlow()

    private val _floatingEnabled = MutableStateFlow(floatingWindowManager.isVisible)
    val floatingEnabled: StateFlow<Boolean> = _floatingEnabled.asStateFlow()

    fun toggleFloatingWindow() {
        _floatingEnabled.value = floatingWindowManager.toggle()
    }

    fun isOverlayPermissionGranted(): Boolean = floatingWindowManager.canDrawOverlays()

    val maxHr: StateFlow<Int> = repository.profile
        .map { it.maxHeartRate() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 190)

    private val profileState = repository.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile())

    private val _currentBpm = MutableStateFlow(0)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private val _heartRateTick = MutableStateFlow(0L)
    val heartRateTick: StateFlow<Long> = _heartRateTick.asStateFlow()

    private var lastBpm = 0

    private val _samples = MutableStateFlow<List<Int>>(emptyList())
    val samples: StateFlow<List<Int>> = _samples.asStateFlow()

    private val _sessionSamples = MutableStateFlow<List<Int>>(emptyList())

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _zoneSeconds = MutableStateFlow(List(HeartRateZone.ZONES.size) { 0 })

    private val _sessionId = MutableStateFlow(0L)
    private var tickerJob: Job? = null

    val sessionStats: StateFlow<SessionStats> =
        combine(_currentBpm, _sessionSamples, _zoneSeconds, profileState) { bpm, samples, zones, profile ->
            if (samples.isEmpty()) SessionStats(current = bpm, zoneSeconds = zones)
            else SessionStats(
                current = bpm,
                min = samples.minOrNull() ?: 0,
                avg = samples.average().toInt(),
                max = samples.maxOrNull() ?: 0,
                calories = estimateCalories(samples, maxHr.value, profile.weightKg),
                zoneSeconds = zones
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionStats())

    init {
        viewModelScope.launch {
            bleManager.heartRate.collect { bpm ->
                android.util.Log.d(
                    "HeartRate",
                    "收到数据 bpm=$bpm time=${System.currentTimeMillis()}"
                )
                _currentBpm.value = bpm
                if (bpm != lastBpm) {
                    lastBpm = bpm
                    _heartRateTick.update { it + 1 }
                }
                _samples.update { (it + bpm).takeLast(300) }
                if (_sessionState.value == SessionState.RUNNING) {
                    _sessionSamples.update { it + bpm }
                    val zoneId = HeartRateZone.from(bpm, maxHr.value).id
                    _zoneSeconds.update { zones ->
                        zones.toMutableList().also { z -> z[zoneId - 1] = z[zoneId - 1] + 1 }
                    }
                    repository.recordHeartRate(_sessionId.value, bpm)
                }
            }
        }

        // 连接成功后记住设备
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    val name = bleManager.connectedDeviceName.value
                    val address = bleManager.connectedDeviceAddress.value
                    if (address != null) repository.saveLastDevice(name, address)
                }
            }
        }

        // 启动时自动重连上一次设备
        viewModelScope.launch {
            val (_, address) = repository.lastDevice.first()
            if (!address.isNullOrBlank() && address != BleManager.SIMULATED_ADDRESS) {
                bleManager.autoConnect(address)
            }
        }
    }

    fun startSession() {
        if (_sessionState.value != SessionState.IDLE) return
        viewModelScope.launch {
            val deviceName = connectedDeviceName.value ?: "未知设备"
            val hr = maxHr.value
            val id = repository.createSession(deviceName, hr)
            _sessionId.value = id
            _sessionSamples.value = emptyList()
            _zoneSeconds.value = List(HeartRateZone.ZONES.size) { 0 }
            _elapsedMillis.value = 0L
            _sessionState.value = SessionState.RUNNING
            startTicker()
        }
    }

    fun pauseSession() {
        if (_sessionState.value == SessionState.RUNNING) {
            _sessionState.value = SessionState.PAUSED
            tickerJob?.cancel()
        }
    }

    fun resumeSession() {
        if (_sessionState.value == SessionState.PAUSED) {
            _sessionState.value = SessionState.RUNNING
            startTicker()
        }
    }

    fun stopSession() {
        if (_sessionState.value == SessionState.IDLE) return
        tickerJob?.cancel()
        viewModelScope.launch {
            val hr = maxHr.value
            val weight = profileState.value.weightKg
            repository.endSession(
                sessionId = _sessionId.value,
                durationMillis = _elapsedMillis.value,
                maxHr = hr,
                weightKg = weight,
                samples = _sessionSamples.value
            )
            _sessionState.value = SessionState.IDLE
            _sessionSamples.value = emptyList()
            _zoneSeconds.value = List(HeartRateZone.ZONES.size) { 0 }
            _elapsedMillis.value = 0L
        }
    }

    fun toggleSimulation() {
        _simulationEnabled.value = bleManager.toggleSimulation()
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    private fun estimateCalories(samples: List<Int>, maxHr: Int, weightKg: Int): Int {
        if (samples.isEmpty() || maxHr <= 0 || weightKg <= 0) return 0
        val avgMets = samples.map {
            METS_BY_ZONE[HeartRateZone.from(it, maxHr).id] ?: 4.0
        }.average()
        val minutes = samples.size / 60.0
        val kcal = avgMets * 3.5 * weightKg / 200.0 * minutes
        return if (kcal.isFinite()) kcal.toInt() else 0
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (_sessionState.value == SessionState.RUNNING) {
                    _elapsedMillis.update { it + 1000 }
                }
            }
        }
    }
}
