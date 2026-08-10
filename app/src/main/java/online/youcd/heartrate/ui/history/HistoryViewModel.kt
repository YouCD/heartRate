package online.youcd.heartrate.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.youcd.heartrate.data.db.SessionEntity
import online.youcd.heartrate.data.repository.HeartRateRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SessionGroup(
    val label: String,
    val sessions: List<SessionEntity>
)

data class HistoryState(
    val groups: List<SessionGroup> = emptyList(),
    val hiddenSessions: List<SessionEntity> = emptyList(),
    val hiddenCount: Int = 0,
    val monthCount: Int = 0,
    val monthDurationMillis: Long = 0
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HeartRateRepository
) : ViewModel() {

    private val allSessions = repository.observeSessions()
        .map { it.sortedByDescending { s -> s.startTime } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    private val _sparklines = MutableStateFlow<Map<Long, List<Int>>>(emptyMap())
    val sparklines: StateFlow<Map<Long, List<Int>>> = _sparklines.asStateFlow()

    init {
        viewModelScope.launch {
            allSessions.collect { list ->
                val (visible, hidden) = list.partition { it.durationMillis > 0 || it.maxBpm > 0 }
                val groups = groupByDate(visible)
                val monthStats = monthStatistics(list)
                _state.value = HistoryState(
                    groups = groups,
                    hiddenSessions = hidden,
                    hiddenCount = hidden.size,
                    monthCount = monthStats.first,
                    monthDurationMillis = monthStats.second
                )

                val ids = visible.map { it.id }
                if (ids.isNotEmpty()) {
                    val rates = repository.getHeartRatesForSessions(ids)
                    _sparklines.value = rates.groupBy { it.sessionId }
                        .mapValues { (_, items) ->
                            downsample(items.sortedBy { it.timestamp }.map { it.bpm }, 24)
                        }
                } else {
                    _sparklines.value = emptyMap()
                }
            }
        }
    }

    fun deleteSessions(ids: List<Long>) {
        viewModelScope.launch { repository.deleteSessions(ids) }
    }

    private fun groupByDate(sessions: List<SessionEntity>): List<SessionGroup> {
        return sessions.groupBy { dateLabel(it.startTime) }
            .map { (label, group) -> SessionGroup(label, group) }
            .sortedWith(compareByDescending<SessionGroup> { it.sessions.first().startTime })
    }

    private fun monthStatistics(list: List<SessionEntity>): Pair<Int, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis
        val monthSessions = list.filter { it.startTime >= monthStart }
        return monthSessions.size to monthSessions.sumOf { it.durationMillis }
    }

    private fun dateLabel(millis: Long): String {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfDay = (millis / 86_400_000L) * 86_400_000L
        val todayStart = (today / 86_400_000L) * 86_400_000L
        return when (startOfDay) {
            todayStart -> "今天"
            todayStart - 86_400_000L -> "昨天"
            else -> {
                val cal = Calendar.getInstance().apply { time = Date(millis) }
                val year = cal.get(Calendar.YEAR)
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                if (year == currentYear) "${month}月${day}日" else "${year}年${month}月${day}日"
            }
        }
    }
}

private fun downsample(values: List<Int>, maxPoints: Int): List<Int> {
    if (values.size <= maxPoints) return values
    val step = values.size.toDouble() / maxPoints
    return (0 until maxPoints).map { i ->
        values[(i * step).toInt().coerceAtMost(values.size - 1)]
    }
}
