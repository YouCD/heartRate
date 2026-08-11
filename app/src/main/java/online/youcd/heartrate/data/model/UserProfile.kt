package online.youcd.heartrate.data.model

import java.util.Calendar

enum class Gender(val displayName: String) {
    MALE("男"),
    FEMALE("女")
}

enum class MaxHrMode(val displayName: String) {
    AUTO("自动"),
    MANUAL("手动")
}

data class UserProfile(
    val nickname: String = "",
    val gender: Gender = Gender.MALE,
    val birthYear: Int = 1996,
    val birthMonth: Int = 1,
    val heightCm: Int = 170,
    val weightKg: Int = 70,
    val maxHrMode: MaxHrMode = MaxHrMode.AUTO,
    val manualMaxHr: Int = 190
) {
    fun age(): Int = computeAge(birthYear, birthMonth)

    fun maxHeartRate(): Int = when (maxHrMode) {
        MaxHrMode.AUTO -> (208 - 0.7 * age()).toInt()
        MaxHrMode.MANUAL -> manualMaxHr
    }

    companion object {
        const val MIN_BIRTH_YEAR = 1900
        const val MIN_HEIGHT = 50
        const val MAX_HEIGHT = 250
        const val MIN_WEIGHT = 2
        const val MAX_WEIGHT = 300

        fun computeAge(birthYear: Int, birthMonth: Int): Int {
            val now = Calendar.getInstance()
            var age = now.get(Calendar.YEAR) - birthYear
            if (birthMonth in 1..12 && now.get(Calendar.MONTH) + 1 < birthMonth) age--
            return age
        }
    }
}