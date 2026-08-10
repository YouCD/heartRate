package online.youcd.heartrate.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import online.youcd.heartrate.data.model.Gender
import online.youcd.heartrate.data.model.MaxHrMode
import online.youcd.heartrate.data.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userDataStore by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private object Keys {
        val NICKNAME = stringPreferencesKey("nickname")
        val GENDER = stringPreferencesKey("gender")
        val AGE = intPreferencesKey("age")
        val HEIGHT = intPreferencesKey("height_cm")
        val WEIGHT = intPreferencesKey("weight_kg")
        val MAX_HR_MODE = stringPreferencesKey("max_hr_mode")
        val MANUAL_MAX_HR = intPreferencesKey("manual_max_hr")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
    }

    val profile: Flow<UserProfile> = context.userDataStore.data.map { prefs ->
        UserProfile(
            nickname = prefs[Keys.NICKNAME] ?: "",
            gender = runCatching { Gender.valueOf(prefs[Keys.GENDER] ?: "") }
                .getOrDefault(Gender.MALE),
            age = prefs[Keys.AGE] ?: 30,
            heightCm = prefs[Keys.HEIGHT] ?: 170,
            weightKg = prefs[Keys.WEIGHT] ?: 70,
            maxHrMode = runCatching { MaxHrMode.valueOf(prefs[Keys.MAX_HR_MODE] ?: "") }
                .getOrDefault(MaxHrMode.AUTO),
            manualMaxHr = prefs[Keys.MANUAL_MAX_HR] ?: 190
        )
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.NICKNAME] = profile.nickname
            prefs[Keys.GENDER] = profile.gender.name
            prefs[Keys.AGE] = profile.age
            prefs[Keys.HEIGHT] = profile.heightCm
            prefs[Keys.WEIGHT] = profile.weightKg
            prefs[Keys.MAX_HR_MODE] = profile.maxHrMode.name
            prefs[Keys.MANUAL_MAX_HR] = profile.manualMaxHr
        }
    }

    val lastDevice: Flow<Pair<String?, String?>> = context.userDataStore.data.map { prefs ->
        prefs[Keys.LAST_DEVICE_NAME] to prefs[Keys.LAST_DEVICE_ADDRESS]
    }

    suspend fun saveLastDevice(name: String?, address: String?) {
        context.userDataStore.edit { prefs ->
            if (name == null) prefs.remove(Keys.LAST_DEVICE_NAME)
            else prefs[Keys.LAST_DEVICE_NAME] = name
            if (address == null) prefs.remove(Keys.LAST_DEVICE_ADDRESS)
            else prefs[Keys.LAST_DEVICE_ADDRESS] = address
        }
    }
}
