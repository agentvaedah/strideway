package id.web.agentvaedah.strideway.core.sensor

/** Decoders for Bluetooth SIG GATT characteristics (all little-endian). */
object GattParsers {
    /** Heart Rate Measurement 0x2A37. */
    fun heartRate(v: ByteArray): Int? {
        if (v.size < 2) return null
        val flags = v.u8(0)
        return if ((flags and 0x01) != 0) {
            if (v.size < 3) null else v.u16(1)
        } else {
            v.u8(1)
        }
    }

    /** Battery Level 0x2A19. */
    fun battery(v: ByteArray): Int? = if (v.isEmpty()) null else v.u8(0).coerceIn(0, 100)

    data class Csc(
        val wheelRevs: Long?,
        val wheelEventTime: Int?,
        val crankRevs: Int?,
        val crankEventTime: Int?,
    )

    /** CSC Measurement 0x2A5B. */
    fun csc(v: ByteArray): Csc {
        if (v.isEmpty()) return Csc(null, null, null, null)
        val flags = v.u8(0)
        var i = 1
        var wheelRevs: Long? = null
        var wheelTime: Int? = null
        var crankRevs: Int? = null
        var crankTime: Int? = null
        if ((flags and 0x01) != 0) {
            if (v.size < i + 6) return Csc(null, null, null, null)
            wheelRevs = v.u32(i)
            wheelTime = v.u16(i + 4)
            i += 6
        }
        if ((flags and 0x02) != 0 && v.size >= i + 4) {
            crankRevs = v.u16(i)
            crankTime = v.u16(i + 2)
        }
        return Csc(wheelRevs, wheelTime, crankRevs, crankTime)
    }

    /** Cycling Power Measurement 0x2A63: flags (uint16) then instantaneous power (sint16). */
    fun cyclingPowerWatts(v: ByteArray): Int? {
        if (v.size < 4) return null
        val raw = v.u16(2)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    /** Environmental Sensing Temperature 0x2A6E: sint16 in 0.01 °C. */
    fun temperatureC(v: ByteArray): Double? {
        if (v.size < 2) return null
        val raw = v.u16(0)
        return (if (raw >= 0x8000) raw - 0x10000 else raw) / 100.0
    }

    /** Environmental Sensing Pressure 0x2A6D: uint32 in 0.1 Pa, returned in hPa. */
    fun pressureHpa(v: ByteArray): Double? = if (v.size < 4) null else v.u32(0) / 1000.0

    /** Environmental Sensing Humidity 0x2A6F: uint16 in 0.01 %. */
    fun humidityPct(v: ByteArray): Double? = if (v.size < 2) null else v.u16(0) / 100.0

    private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
    private fun ByteArray.u16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
    private fun ByteArray.u32(i: Int): Long = (u16(i).toLong()) or (u16(i + 2).toLong() shl 16)
}
