package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManeuverParseTest {
    // Shape of a real brouter.de answer with turnInstructionMode=2: [pointIndex, command, exit, distanceToNext, angle].
    private val json = """
        {"features":[{"properties":{"track-length":"300",
           "voicehints":[[1,5,0,100.0,90],[2,3,0,100.0,-15],[3,14,-3,50.0,138],[4,99,0,0.0,0]]},
          "geometry":{"coordinates":[[106.80,-6.20],[106.801,-6.20],[106.802,-6.20],[106.803,-6.20],[106.804,-6.20]]}}]}
    """.trimIndent()

    @Test
    fun parsesVoiceHints() {
        val m = BrouterParser.parse(json)!!.maneuvers
        assertEquals(listOf(1, 2, 3), m.map { it.pointIndex })
        assertEquals(ManeuverType.RIGHT, m[0].type)
        assertEquals(ManeuverType.SLIGHT_LEFT, m[1].type)
        assertEquals(ManeuverType.ROUNDABOUT, m[2].type)
        assertEquals(3, m[2].exit)
    }

    @Test
    fun noHintsMeansEmpty() {
        val bare = """{"features":[{"properties":{},"geometry":{"coordinates":[[1.0,2.0],[1.1,2.0]]}}]}"""
        assertTrue(BrouterParser.parse(bare)!!.maneuvers.isEmpty())
    }

    @Test
    fun commandMapping() {
        assertEquals(ManeuverType.STRAIGHT, ManeuverType.fromBrouter(1))
        assertEquals(ManeuverType.LEFT, ManeuverType.fromBrouter(2))
        assertEquals(ManeuverType.SHARP_RIGHT, ManeuverType.fromBrouter(7))
        assertEquals(ManeuverType.KEEP_LEFT, ManeuverType.fromBrouter(8))
        assertEquals(ManeuverType.U_TURN, ManeuverType.fromBrouter(15))
        assertNull(ManeuverType.fromBrouter(99))
    }

    @Test
    fun spokenPhrases() {
        assertEquals("belok kanan", ManeuverType.RIGHT.phrase(0))
        assertEquals("masuk bundaran, ambil keluar ke-2", ManeuverType.ROUNDABOUT.phrase(2))
        assertEquals("putar balik", ManeuverType.U_TURN.phrase(0))
    }
}

class GeometryManeuversTest {
    private val o = LatLon(-6.2, 106.8)

    @Test
    fun classifiesTurnSharpness() {
        val corner = Geo.destination(o, 0.0, 300.0)
        fun route(turnDeg: Double) = buildList {
            for (d in 0..300 step 10) add(Geo.destination(o, 0.0, d.toDouble()))
            for (d in 10..300 step 10) add(Geo.destination(corner, turnDeg, d.toDouble()))
        }
        assertEquals(ManeuverType.SLIGHT_RIGHT, Navigator.fromGeometry(route(30.0)).single().type)
        assertEquals(ManeuverType.RIGHT, Navigator.fromGeometry(route(90.0)).single().type)
        assertEquals(ManeuverType.SHARP_LEFT, Navigator.fromGeometry(route(-130.0 + 360.0)).single().type)
    }
}

class NavigatorTest {
    private val o = LatLon(-6.2, 106.8)
    private val corner = Geo.destination(o, 0.0, 600.0)
    private val end = Geo.destination(corner, 90.0, 400.0)
    private val route = buildList {
        for (d in 0..600 step 10) add(Geo.destination(o, 0.0, d.toDouble()))
        for (d in 10..400 step 10) add(Geo.destination(corner, 90.0, d.toDouble()))
    }
    private val maneuvers = listOf(Maneuver(60, ManeuverType.RIGHT))

    private fun at(m: Double) = Geo.destination(o, 0.0, m)

    @Test
    fun twoStageAnnouncements() {
        val n = Navigator(route, maneuvers)
        val spoken = ArrayList<NavEvent>()
        var t = 0L
        for (m in 0..600 step 5) {
            spoken += n.update(at(m.toDouble()), t, speedMps = 5.0).events
            t += 1_000
        }
        val turns = spoken.filterIsInstance<NavEvent.Prepare>() + spoken.filterIsInstance<NavEvent.Now>()
        assertEquals(1, spoken.count { it is NavEvent.Prepare })
        assertEquals(1, spoken.count { it is NavEvent.Now })
        assertTrue(turns.all { (it as? NavEvent.Prepare)?.maneuver?.type ?: (it as NavEvent.Now).maneuver.type == ManeuverType.RIGHT })
    }

    @Test
    fun liveDistanceAndEta() {
        val n = Navigator(route, maneuvers)
        val s = n.update(at(400.0), 0, speedMps = 5.0)
        assertEquals(ManeuverType.RIGHT, s.next!!.maneuver.type)
        assertEquals(200.0, s.next!!.inM, 2.0)
        assertEquals(600.0, s.remainingM, 2.0)
        assertEquals(120, s.etaS)
    }

    @Test
    fun arrivalFiresOnce() {
        val n = Navigator(route, maneuvers)
        n.update(corner, 0, 5.0)
        var arrived = 0
        var t = 1_000L
        for (d in 0..400 step 10) {
            arrived += n.update(Geo.destination(corner, 90.0, d.toDouble()), t, 5.0).events.count { it is NavEvent.Arrived }
            t += 1_000
        }
        assertEquals(1, arrived)
    }

    @Test
    fun offRouteStillReported() {
        val n = Navigator(route, maneuvers)
        n.update(at(100.0), 0, 5.0)
        n.update(Geo.destination(at(100.0), 90.0, 80.0), 1_000, 5.0)
        val s = n.update(Geo.destination(at(110.0), 90.0, 80.0), 7_000, 5.0)
        assertTrue(s.offRoute)
        assertTrue(s.events.any { it is NavEvent.OffRoute })
    }

    @Test
    fun spokenText() {
        assertEquals("Dalam 200 meter, belok kanan", NavEvent.Prepare(Maneuver(1, ManeuverType.RIGHT), 196.0).text())
        assertEquals("Belok kanan sekarang", NavEvent.Now(Maneuver(1, ManeuverType.RIGHT)).text())
        assertEquals("Anda sudah sampai di tujuan", NavEvent.Arrived.text())
    }
}
