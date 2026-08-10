package online.youcd.heartrate.data.ble

object HeartRateParser {

    fun parseMeasurement(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val flag = data[0].toInt() and 0xFF
        val is16Bit = flag and 0x01 != 0
        if (data.size < (if (is16Bit) 3 else 2)) return null
        val bpm = if (is16Bit) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            data[1].toInt() and 0xFF
        }
        return if (bpm in 0..250) bpm else null
    }
}
