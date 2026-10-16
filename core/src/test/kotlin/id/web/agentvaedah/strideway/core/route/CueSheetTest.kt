package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CueSheetTest {
    private val a = LatLon(-6.6, 106.8)
    private val b = Geo.destination(a, 90.0, 1_000.0)
    private val c = Geo.destination(b, 0.0, 500.0)
    private val d = Geo.destination(c, 90.0, 800.0)
    private val route = listOf(a, b, c, d)
    private val maneuvers = listOf(
        Maneuver(2, ManeuverType.RIGHT),
        Maneuver(1, ManeuverType.LEFT),
        Maneuver(3, ManeuverType.STRAIGHT),
        Maneuver(9, ManeuverType.LEFT),
    )

    @Test
    fun `turns come in riding order with distance from the start`() {
        val cues = CueSheet.of(route, maneuvers)
        assertEquals(listOf(ManeuverType.LEFT, ManeuverType.RIGHT), cues.map { it.maneuver.type })
        assertEquals(1_000.0, cues[0].atM, 1.0)
        assertEquals(1_500.0, cues[1].atM, 1.0)
    }

    @Test
    fun `next cue counts the metres still to ride`() {
        val cues = CueSheet.of(route, maneuvers)
        val up = CueSheet.next(cues, 1_200.0)!!
        assertEquals(ManeuverType.RIGHT, up.maneuver.type)
        assertEquals(300.0, up.inM, 1.0)
        assertNull(CueSheet.next(cues, 1_600.0))
    }
}
