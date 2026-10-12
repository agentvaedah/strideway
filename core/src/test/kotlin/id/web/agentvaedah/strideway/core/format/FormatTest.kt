package id.web.agentvaedah.strideway.core.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatTest {
    @Test
    fun durationUnderAnHourHasNoHourField() {
        assertEquals("5:09", Format.duration(309_000))
        assertEquals("0:00", Format.duration(0))
    }

    @Test
    fun durationOverAnHour() {
        assertEquals("1:02:03", Format.duration(3_723_000))
    }

    @Test
    fun kmTwoDecimals() {
        assertEquals("12.35", Format.km(12_346.0))
    }

    @Test
    fun paceMinutesPerKm() {
        assertEquals("5:33", Format.pace(3.0))
        assertEquals("--:--", Format.pace(0.1))
    }
}
