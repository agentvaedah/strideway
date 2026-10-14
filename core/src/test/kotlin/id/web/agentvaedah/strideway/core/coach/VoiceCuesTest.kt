package id.web.agentvaedah.strideway.core.coach

import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.physio.Zones
import id.web.agentvaedah.strideway.core.route.RouteEvent
import id.web.agentvaedah.strideway.core.route.TurnDirection
import id.web.agentvaedah.strideway.core.telemetry.LiveMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoiceCuesTest {
    private val zones = Zones.heartRate(190, 60)
    private fun cues(sport: Sport = Sport.RIDE, config: CueConfig = CueConfig()) = VoiceCues(sport, zones, config)

    @Test
    fun splitAtEachWholeKilometre() {
        val v = cues()
        assertTrue(v.update(LiveMetrics(distanceM = 990.0, movingMs = 120_000), 0).isEmpty())
        val c1 = v.update(LiveMetrics(distanceM = 1_005.0, movingMs = 122_000, avgSpeedKmh = 29.5), 1_000)
        assertEquals(1, c1.size)
        assertEquals(CueKind.SPLIT, c1[0].kind)
        assertTrue(c1[0].text.startsWith("1 kilometer"), c1[0].text)
        assertTrue(c1[0].text.contains("29,5 kilometer per jam"), c1[0].text)
        assertTrue(v.update(LiveMetrics(distanceM = 1_500.0, movingMs = 180_000), 2_000).isEmpty())
        val c2 = v.update(LiveMetrics(distanceM = 2_010.0, movingMs = 240_000), 3_000)
        assertTrue(c2.single().text.startsWith("2 kilometer"))
    }

    @Test
    fun runSplitSaysPace() {
        val v = cues(Sport.RUN)
        v.update(LiveMetrics(distanceM = 0.0), 0)
        val c = v.update(LiveMetrics(distanceM = 1_001.0, movingMs = 330_000, avgSpeedKmh = 10.9), 1_000)
        // 1 km in 5:30 moving time.
        assertTrue(c.single().text.contains("5 menit 30 detik per kilometer"), c.single().text)
    }

    @Test
    fun pauseAndResume() {
        val v = cues()
        v.update(LiveMetrics(), 0)
        assertEquals("Jeda otomatis", v.update(LiveMetrics(autoPaused = true), 1_000).single().text)
        assertEquals("Lanjut merekam", v.update(LiveMetrics(), 2_000).single().text)
        assertEquals("Rekaman dijeda", v.update(LiveMetrics(manualPaused = true), 3_000).single().text)
    }

    @Test
    fun hrHighZoneWithCooldown() {
        val v = cues()
        val z4 = zones[3].low
        v.update(LiveMetrics(heartRate = 120), 0)
        val c = v.update(LiveMetrics(heartRate = z4), 1_000)
        assertEquals(CueKind.HEART_RATE, c.single().kind)
        v.update(LiveMetrics(heartRate = 120), 30_000)
        assertTrue(v.update(LiveMetrics(heartRate = z4), 60_000).isEmpty())
        v.update(LiveMetrics(heartRate = 120), 100_000)
        assertEquals(1, v.update(LiveMetrics(heartRate = z4), 200_000).size)
    }

    @Test
    fun disabledKindsAreSilent() {
        val v = cues(config = CueConfig(split = false, pause = false, heartRate = false, route = false))
        v.update(LiveMetrics(), 0)
        assertTrue(v.update(LiveMetrics(distanceM = 1_100.0, autoPaused = true, heartRate = 189), 1_000).isEmpty())
        assertTrue(v.route(RouteEvent.LeftRoute(80.0)) == null)
    }

    @Test
    fun routeEventTexts() {
        val v = cues()
        assertEquals("Keluar rute, 80 meter dari jalur", v.route(RouteEvent.LeftRoute(80.0))!!.text)
        assertEquals("Kembali di rute", v.route(RouteEvent.BackOnRoute)!!.text)
        assertEquals("Belok kanan dalam 100 meter", v.route(RouteEvent.TurnAhead(TurnDirection.RIGHT, 96.0))!!.text)
        assertEquals(CueKind.ROUTE_ALERT, v.route(RouteEvent.LeftRoute(80.0))!!.kind)
    }

    @Test
    fun startAndStopLines() {
        assertEquals("Sudah siap gowes? Gas, hati-hati di jalan!", VoiceCues.startLine(Sport.RIDE))
        assertEquals("Sudah siap lari? Gas, hati-hati di jalan!", VoiceCues.startLine(Sport.RUN))
        assertEquals("Rekaman berhenti. Selamat istirahat.", VoiceCues.STOP_LINE)
    }

    @Test
    fun pauseCuesAreOnByDefault() {
        assertTrue(CueConfig().pause)
    }
}
