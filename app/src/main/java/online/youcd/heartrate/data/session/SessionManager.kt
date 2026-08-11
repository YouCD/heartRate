package online.youcd.heartrate.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.data.repository.HeartRateRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 共享的会话计时与状态管理。悬浮窗与心率界面都订阅这里的
 * [elapsedMillis]，保证同一次运动的时长显示一致。
 */
@Singleton
class SessionManager @Inject constructor(
    private val repository: HeartRateRepository
) {
    enum class SessionState { IDLE, RUNNING, PAUSED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _sessionId = MutableStateFlow(0L)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

    private val _sessionSamples = MutableStateFlow<List<Int>>(emptyList())
    val sessionSamples: StateFlow<List<Int>> = _sessionSamples.asStateFlow()

    private val _zoneSeconds = MutableStateFlow(List(HeartRateZone.ZONES.size) { 0 })
    val zoneSeconds: StateFlow<List<Int>> = _zoneSeconds.asStateFlow()

    private var tickerJob: Job? = null

    private var lastZoneEpoch: Long = 0L
    private var zoneCarryMillis: Long = 0L

    val isRunning: Boolean
        get() = _sessionState.value == SessionState.RUNNING

    fun startSession(deviceName: String, maxHr: Int) {
        if (_sessionState.value != SessionState.IDLE) return
        scope.launch {
            val id = repository.createSession(deviceName, maxHr)
            _sessionId.value = id
            _sessionSamples.value = emptyList()
            _zoneSeconds.value = List(HeartRateZone.ZONES.size) { 0 }
            _elapsedMillis.value = 0L
            lastZoneEpoch = 0L
            zoneCarryMillis = 0L
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

    fun stopSession(maxHr: Int, weightKg: Int) {
        if (_sessionState.value == SessionState.IDLE) return
        tickerJob?.cancel()
        scope.launch {
            repository.endSession(
                sessionId = _sessionId.value,
                durationMillis = _elapsedMillis.value,
                maxHr = maxHr,
                weightKg = weightKg,
                samples = _sessionSamples.value
            )
            _sessionState.value = SessionState.IDLE
            _sessionSamples.value = emptyList()
            _zoneSeconds.value = List(HeartRateZone.ZONES.size) { 0 }
            _elapsedMillis.value = 0L
            lastZoneEpoch = 0L
            zoneCarryMillis = 0L
        }
    }

    /** 心率数据入口：仅在会话运行期间累计样本、区间与落库。 */
    suspend fun onHeartRate(bpm: Int, maxHr: Int) {
        if (_sessionState.value != SessionState.RUNNING) return
        val zoneId = HeartRateZone.from(bpm, maxHr).id
        val now = System.currentTimeMillis()
        val elapsedSinceLast = if (lastZoneEpoch == 0L) 1000L else now - lastZoneEpoch
        lastZoneEpoch = now
        zoneCarryMillis += elapsedSinceLast
        val seconds = (zoneCarryMillis / 1000L).toInt()
        zoneCarryMillis %= 1000L
        _sessionSamples.update { it + bpm }
        if (seconds > 0) {
            _zoneSeconds.update { zones ->
                zones.toMutableList().also { z -> z[zoneId - 1] = z[zoneId - 1] + seconds }
            }
        }
        repository.recordHeartRate(_sessionId.value, bpm)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (_sessionState.value == SessionState.RUNNING) {
                    _elapsedMillis.update { it + 1000 }
                }
            }
        }
    }
}