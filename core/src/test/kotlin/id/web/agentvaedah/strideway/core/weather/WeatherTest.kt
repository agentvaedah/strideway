package id.web.agentvaedah.strideway.core.weather

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeatherTest {
    @Test
    fun wmoCodes() {
        assertEquals(WeatherKind.CLEAR, WeatherCodes.kind(0))
        assertEquals(WeatherKind.PARTLY_CLOUDY, WeatherCodes.kind(2))
        assertEquals(WeatherKind.FOG, WeatherCodes.kind(45))
        assertEquals(WeatherKind.DRIZZLE, WeatherCodes.kind(53))
        assertEquals(WeatherKind.RAIN, WeatherCodes.kind(63))
        assertEquals(WeatherKind.RAIN, WeatherCodes.kind(81))
        assertEquals(WeatherKind.THUNDERSTORM, WeatherCodes.kind(95))
        assertEquals(WeatherKind.UNKNOWN, WeatherCodes.kind(999))
        assertEquals("Hujan", WeatherKind.RAIN.label)
    }

    @Test
    fun windDirectionLabelsAreWhereWindComesFrom() {
        assertEquals("dari Barat", WeatherCodes.windFrom(270.0))
        assertEquals("dari Utara", WeatherCodes.windFrom(5.0))
    }

    @Test
    fun rideAdviceFlagsHeatRainAndUv() {
        val hot = WeatherCodes.rideAdvice(tempC = 35.0, rainProbPct = 10, uvIndex = 11.0, windKmh = 10.0, kind = WeatherKind.CLEAR)
        assertTrue(hot.any { it.contains("panas", ignoreCase = true) })
        assertTrue(hot.any { it.contains("UV", ignoreCase = false) })
        val wet = WeatherCodes.rideAdvice(tempC = 26.0, rainProbPct = 80, uvIndex = 2.0, windKmh = 10.0, kind = WeatherKind.RAIN)
        assertTrue(wet.any { it.contains("hujan", ignoreCase = true) })
        val storm = WeatherCodes.rideAdvice(26.0, 70, 1.0, 45.0, WeatherKind.THUNDERSTORM)
        assertTrue(storm.first().contains("petir", ignoreCase = true))
        val fine = WeatherCodes.rideAdvice(24.0, 5, 3.0, 8.0, WeatherKind.CLEAR)
        assertEquals(1, fine.size)
    }
}
