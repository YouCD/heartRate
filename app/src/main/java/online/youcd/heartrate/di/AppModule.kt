package online.youcd.heartrate.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import online.youcd.heartrate.data.db.HeartRateDao
import online.youcd.heartrate.data.db.HeartRateDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HeartRateDatabase =
        HeartRateDatabase.getInstance(context)

    @Provides
    fun provideHeartRateDao(database: HeartRateDatabase): HeartRateDao =
        database.heartRateDao()
}
