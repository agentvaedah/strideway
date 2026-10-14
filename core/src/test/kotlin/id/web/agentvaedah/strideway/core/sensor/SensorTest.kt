package id.web.agentvaedah.strideway.core.sensor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GattParsersTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun heartRateUint8() {
        assertEquals(72, GattParsers.heartRate(bytes(0x00, 72)))
    }

    @Test
    fun heartRateUint16() {
        assertEquals(300, GattParsers.heartRate(bytes(0x01, 0x2C, 0x01)))
    }

    @Test
    fun heartRateTooShort() {
        assertNull(GattParsers.heartRate(bytes(0x00)))
    }

    @Test
    fun cscWheelAndCrank() {
        val v = bytes(0x03, 0x10, 0x00, 0x00, 0x00, 0x00, 0x04, 0x05, 0x00, 0x00, 0x08)
        val c = GattParsers.csc(v)
        assertEquals(16L, c.wheelRevs)
        assertEquals(1024, c.wheelEventTime)
        assertEquals(5, c.crankRevs)
        assertEquals(2048, c.crankEventTime)
    }

    @Test
    fun cscCrankOnly() {
        val c = GattParsers.csc(bytes(0x02, 0x05, 0x00, 0x00, 0x04))
        assertNull(c.wheelRevs)
        assertEquals(5, c.crankRevs)
        assertEquals(1024, c.crankEventTime)
    }

    @Test
    fun cyclingPowerSigned() {
        assertEquals(250, GattParsers.cyclingPowerWatts(bytes(0x00, 0x00, 0xFA, 0x00)))
        assertEquals(-2, GattParsers.cyclingPowerWatts(bytes(0x00, 0x00, 0xFE, 0xFF)))
    }
}

class CadenceCalculatorTest {
    @Test
    fun ninetyRpm() {
        val c = CadenceCalculator()
        assertNull(c.update(0, 0, 0))
        // 3 revolutions in 2 s = 90 rpm
        assertEquals(90, c.update(3, 2048, 2000))
    }

    @Test
    fun survivesCounterRollover() {
        val c = CadenceCalculator()
        c.update(65_535, 65_000, 0)
        // +2 revs, +1024 ticks across both rollovers = 120 rpm
        assertEquals(120, c.update(1, (65_000 + 1024) and 0xFFFF, 1000))
    }

    @Test
    fun dropsToZeroWhenCrankStops() {
        val c = CadenceCalculator()
        c.update(0, 0, 0)
        c.update(3, 2048, 2000)
        assertEquals(90, c.update(3, 2048, 4000))
        assertEquals(0, c.update(3, 2048, 5001))
    }

    @Test
    fun wheelSpeed() {
        val w = WheelSpeedCalculator(2.105)
        w.update(100, 0, 0)
        // 5 revs in 1 s = 10.525 m/s
        assertEquals(10.525, w.update(105, 1024, 1000)!!, 1e-9)
    }
}

class EnvironmentalParsersTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun temperatureIsSint16HundredthsOfDegree() {
        // 0x0A1D = 2589 -> 25.89 °C
        assertEquals(25.89, GattParsers.temperatureC(bytes(0x1D, 0x0A))!!, 1e-9)
        // -5.00 °C = -500 = 0xFE0C
        assertEquals(-5.0, GattParsers.temperatureC(bytes(0x0C, 0xFE))!!, 1e-9)
        assertNull(GattParsers.temperatureC(bytes(0x01)))
    }

    @Test
    fun pressureIsUint32TenthsOfPascal() {
        // 1013250 (0x000F7602) tenths of Pa -> 1013.25 hPa
        assertEquals(1013.25, GattParsers.pressureHpa(bytes(0x02, 0x76, 0x0F, 0x00))!!, 1e-9)
        assertNull(GattParsers.pressureHpa(bytes(0x00, 0x01)))
    }

    @Test
    fun humidityIsUint16HundredthsOfPercent() {
        assertEquals(65.5, GattParsers.humidityPct(bytes(0x96, 0x19))!!, 1e-9)
    }
}
