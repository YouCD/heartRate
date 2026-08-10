package online.youcd.heartrate.data.model

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
    val age: Int = 30,
    val heightCm: Int = 170,
    val weightKg: Int = 70,
    val maxHrMode: MaxHrMode = MaxHrMode.AUTO,
    val manualMaxHr: Int = 190
) {
    fun maxHeartRate(): Int = when (maxHrMode) {
        MaxHrMode.AUTO -> (208 - 0.7 * age).toInt()
        MaxHrMode.MANUAL -> manualMaxHr
    }

    companion object {
        const val MIN_AGE = 1
        const val MAX_AGE = 120
        const val MIN_HEIGHT = 50
        const val MAX_HEIGHT = 250
        const val MIN_WEIGHT = 2
        const val MAX_WEIGHT = 300
    }
}
