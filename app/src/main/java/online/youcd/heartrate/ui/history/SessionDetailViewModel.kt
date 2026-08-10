package online.youcd.heartrate.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.youcd.heartrate.data.db.HeartRateEntity
import online.youcd.heartrate.data.db.SessionEntity
import online.youcd.heartrate.data.repository.HeartRateRepository
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val repository: HeartRateRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    val session: StateFlow<SessionEntity?> = repository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val heartRates: StateFlow<List<HeartRateEntity>> = repository.observeHeartRates(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSession() {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }
}
