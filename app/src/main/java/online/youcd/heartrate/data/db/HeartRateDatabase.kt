package online.youcd.heartrate.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HeartRateEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HeartRateDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao

    companion object {
        @Volatile
        private var instance: HeartRateDatabase? = null

        fun getInstance(context: Context): HeartRateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HeartRateDatabase::class.java,
                    "heartrate.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
            }
    }
}
