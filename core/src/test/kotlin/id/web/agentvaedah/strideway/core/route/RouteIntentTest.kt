package id.web.agentvaedah.strideway.core.route

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RouteIntentTest {
    @Test
    fun parsesJsonWrappedInProse() {
        val text = """
            Berikut rutenya:
            ```json
            {"start": "Kalibata, Jakarta", "waypoints": ["Monas, Jakarta", "Jalan Sudirman, Jakarta", ""],
             "end": null, "sport": "road", "surface": "paved", "elevation": "flat", "target_km": 50, "loop": true, "extra": 1}
            ```
        """.trimIndent()
        val i = RouteIntents.parse(text)
        assertEquals("Kalibata, Jakarta", i.start)
        assertEquals(listOf("Monas, Jakarta", "Jalan Sudirman, Jakarta"), i.waypoints)
        assertNull(i.end)
        assertEquals(50.0, i.targetKm)
        assertTrue(i.loop)
        assertEquals("fastbike", RouteIntents.brouterProfile(i))
    }

    @Test
    fun profileByActivity() {
        assertEquals("hiking-beta", RouteIntents.brouterProfile(RouteIntent(start = "A", sport = "run")))
        assertEquals("mtb", RouteIntents.brouterProfile(RouteIntent(start = "A", sport = "mtb")))
        assertEquals("trekking", RouteIntents.brouterProfile(RouteIntent(start = "A", surface = "unpaved")))
    }

    @Test
    fun noJsonIsAnError() {
        assertThrows<RouteIntentException> { RouteIntents.parse("Maaf, saya tidak bisa.") }
    }

    @Test
    fun blankStartIsAnError() {
        assertThrows<RouteIntentException> { RouteIntents.parse("""{"start": " "}""") }
    }
}
