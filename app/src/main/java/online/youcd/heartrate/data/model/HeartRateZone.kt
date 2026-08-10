package online.youcd.heartrate.data.model

data class HeartRateZone(
    val id: Int,
    val displayName: String,
    val minPercent: Float,
    val maxPercent: Float,
    val color: Long
) {
    companion object {
        val ZONES = listOf(
            HeartRateZone(1, "HR Z1", 0.0f, 0.6f, 0xFF8E8E93),
            HeartRateZone(2, "HR Z2", 0.6f, 0.7f, 0xFF0A84FF),
            HeartRateZone(3, "HR Z3", 0.7f, 0.8f, 0xFF34C759),
            HeartRateZone(4, "HR Z4", 0.8f, 0.9f, 0xFFFFCC00),
            HeartRateZone(5, "HR Z5", 0.9f, 1.2f, 0xFFFF3B30)
        )

        fun from(bpm: Int, maxHr: Int): HeartRateZone {
            val ratio = if (maxHr <= 0) 0f else bpm.toFloat() / maxHr
            return ZONES.first { ratio >= it.minPercent && ratio < it.maxPercent }
        }

        fun zoneColor(bpm: Int, maxHr: Int): Long =
            from(bpm, maxHr).color
    }
}
