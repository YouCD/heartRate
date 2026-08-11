package online.youcd.heartrate.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.youcd.heartrate.data.backup.BackupManager
import online.youcd.heartrate.data.model.UserProfile
import online.youcd.heartrate.data.repository.HeartRateRepository
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: HeartRateRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    val profile: StateFlow<UserProfile> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch { repository.saveProfile(profile) }
    }

    suspend fun buildBackupZip(): ByteArray {
        val profile = repository.profile.first()
        val sessions = repository.getAllSessions()
        val heartRates = repository.getHeartRatesForSessions(sessions.map { it.id })
        return backupManager.buildZip(profile, sessions, heartRates)
    }

    suspend fun restoreFromZip(input: InputStream): Boolean = runCatching {
        val backup = backupManager.parse(input)
        repository.importBackup(backup.profile, backup.sessions, backup.heartRates)
    }.isSuccess
}