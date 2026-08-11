package online.youcd.heartrate.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startTime ASC")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM heart_rates WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeHeartRates(sessionId: Long): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rates WHERE sessionId IN (:sessionIds) ORDER BY sessionId, timestamp")
    suspend fun getHeartRatesBySessionIds(sessionIds: List<Long>): List<HeartRateEntity>

    @Insert
    suspend fun insertHeartRate(heartRate: HeartRateEntity): Long

    @Insert
    suspend fun insertHeartRates(heartRates: List<HeartRateEntity>)

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun sessionCount(): Int

    @Query("DELETE FROM heart_rates")
    suspend fun deleteAllHeartRates()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<Long>)
}
