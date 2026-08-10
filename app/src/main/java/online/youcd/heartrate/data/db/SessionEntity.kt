package online.youcd.heartrate.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions"
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long = 0,
    val durationMillis: Long = 0,
    val avgBpm: Int = 0,
    val maxBpm: Int = 0,
    val minBpm: Int = 0,
    val calories: Int = 0,
    val maxHr: Int = 0,
    val zoneSeconds: String = "",
    val deviceName: String = "",
    val note: String = ""
)
