package online.youcd.heartrate.data.repository

import kotlinx.coroutines.flow.Flow
import online.youcd.heartrate.data.db.HeartRateDao
import online.youcd.heartrate.data.db.HeartRateEntity
import online.youcd.heartrate.data.db.SessionEntity
import online.youcd.heartrate.data.local.UserPreferences
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.data.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartRateRepository @Inject constructor(
    private val dao: HeartRateDao,
    private val userPreferences: UserPreferences
) {

    val profile: Flow<UserProfile> = userPreferences.profile

    suspend fun saveProfile(profile: UserProfile) {
        userPreferences.saveProfile(profile)
    }

    val lastDevice: Flow<Pair<String?, String?>> = userPreferences.lastDevice

    suspend fun saveLastDevice(name: String?, address: String?) {
        userPreferences.saveLastDevice(name, address)
    }

    suspend fun createSession(deviceName: String, maxHr: Int): Long =
        dao.insertSession(
            SessionEntity(
                startTime = System.currentTimeMillis(),
                deviceName = deviceName,
                maxHr = maxHr
            )
        )

    suspend fun recordHeartRate(sessionId: Long, bpm: Int) {
        dao.insertHeartRate(
            HeartRateEntity(
                sessionId = sessionId,
                timestamp = System.currentTimeMillis(),
                bpm = bpm
            )
        )
    }

    suspend fun endSession(
        sessionId: Long,
        durationMillis: Long,
        maxHr: Int,
        weightKg: Int,
        samples: List<Int>
    ) {
        val avgBpm = if (samples.isEmpty()) 0 else samples.average().toInt()
        val maxBpm = samples.maxOrNull() ?: 0
        val minBpm = samples.minOrNull() ?: 0
        val calories = estimateCalories(samples, maxHr, weightKg)
        val zoneSeconds = buildZoneSeconds(samples, maxHr)

        val base = dao.getSession(sessionId)
        if (base != null) {
            dao.updateSession(
                base.copy(
                    endTime = System.currentTimeMillis(),
                    durationMillis = durationMillis,
                    avgBpm = avgBpm,
                    maxBpm = maxBpm,
                    minBpm = minBpm,
                    calories = calories,
                    zoneSeconds = zoneSeconds
                )
            )
        }
    }

    private val METS_BY_ZONE = mapOf(
        1 to 1.2,
        2 to 3.0,
        3 to 5.0,
        4 to 7.0,
        5 to 9.5
    )

    private fun estimateCalories(samples: List<Int>, maxHr: Int, weightKg: Int): Int {
        if (samples.isEmpty() || maxHr <= 0 || weightKg <= 0) return 0
        val avgMets = samples.map {
            METS_BY_ZONE[HeartRateZone.from(it, maxHr).id] ?: 4.0
        }.average()
        val minutes = samples.size / 60.0
        val kcal = avgMets * 3.5 * weightKg / 200.0 * minutes
        return if (kcal.isFinite()) kcal.toInt() else 0
    }

    private fun buildZoneSeconds(samples: List<Int>, maxHr: Int): String {
        val map = HeartRateZone.ZONES.associate { it.displayName to 0 }.toMutableMap()
        for (bpm in samples) {
            val name = HeartRateZone.from(bpm, maxHr).displayName
            map[name] = (map[name] ?: 0) + 1
        }
        return map.map { (k, v) -> "$k:$v" }.joinToString(",")
    }

    fun observeSessions(): Flow<List<SessionEntity>> = dao.observeSessions()

    fun observeSession(sessionId: Long): Flow<SessionEntity?> = dao.observeSession(sessionId)

    fun observeHeartRates(sessionId: Long): Flow<List<HeartRateEntity>> =
        dao.observeHeartRates(sessionId)

    suspend fun getAllSessions(): List<SessionEntity> = dao.getAllSessions()

    suspend fun importBackup(
        profile: UserProfile?,
        sessions: List<SessionEntity>,
        heartRates: List<HeartRateEntity>
    ) {
        dao.deleteAllHeartRates()
        dao.deleteAllSessions()
        sessions.forEach { dao.insertSession(it) }
        dao.insertHeartRates(heartRates)
        if (profile != null) userPreferences.saveProfile(profile)
    }

    suspend fun clearAll() {
        dao.deleteAllHeartRates()
        dao.deleteAllSessions()
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSession(sessionId)
    }

    suspend fun deleteSessions(sessionIds: List<Long>) {
        dao.deleteSessions(sessionIds)
    }

    suspend fun getHeartRatesForSessions(sessionIds: List<Long>): List<HeartRateEntity> =
        dao.getHeartRatesBySessionIds(sessionIds)
}
