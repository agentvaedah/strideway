package id.web.agentvaedah.strideway.core.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ShareLayoutTest {
    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("cases")
    fun `nothing overlaps and everything stays inside the safe area`(template: ShareTemplate, format: ShareFormat, case: String) {
        val input = inputs.getValue(case)
        val elements = ShareLayout.layout(template, format, input, EstimatedMeasure)
        val problems = ShareLayout.problems(elements, format, EstimatedMeasure)
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("cases")
    fun `every stat is drawn`(template: ShareTemplate, format: ShareFormat, case: String) {
        val input = inputs.getValue(case)
        val elements = ShareLayout.layout(template, format, input, EstimatedMeasure)
        val values = elements.filterIsInstance<ShareElement.Value>().map { it.value }
        val labels = elements.filterIsInstance<ShareElement.Label>().joinToString(" ") { it.text }
        // Elevation and Card show the distance, time and climb only.
        val expected = if (template.maxStats < input.stats.size) input.stats.filter { it.label in setOf("Distance", "Time", "Elev Gain") } else input.stats
        for (s in expected) {
            // Minimal and the photo-and-map strip fold the smaller figures into one caption line.
            assertTrue(s.value in values || labels.contains(s.value), "${s.label} missing from ${template.name}")
        }
    }

    @Test
    fun `five stats fit on the transparent feed card`() {
        val elements = ShareLayout.layout(ShareTemplate.ROUTE_ONLY, ShareFormat.FEED, inputs.getValue("five"), EstimatedMeasure)
        assertEquals(5, elements.filterIsInstance<ShareElement.Value>().size)
    }

    @Test
    fun `map templates draw tiles and the transparent one does not`() {
        for (t in ShareTemplate.entries) {
            val input = inputs.getValue("record")
            val maps = ShareLayout.layout(t, ShareFormat.FEED, input, EstimatedMeasure).filterIsInstance<ShareElement.MapArea>()
            if (t.usesMap) assertTrue(maps.any { it.tiles }, "${t.name} should show map tiles")
            else assertFalse(maps.any { it.tiles }, "${t.name} should not show map tiles")
        }
    }

    @Test
    fun `a long title is shortened instead of running off the card`() {
        val elements = ShareLayout.layout(ShareTemplate.BOTTOM_STATS, ShareFormat.FEED, inputs.getValue("extreme"), EstimatedMeasure)
        val title = elements.filterIsInstance<ShareElement.Label>().first { it.text.startsWith("Trans") }
        assertTrue(title.text.endsWith("..."))
    }

    @Test
    fun `elevation and record cards need their data`() {
        val plain = inputs.getValue("four").copy(elevationMax = null, record = null)
        assertFalse(ShareLayout.available(ShareTemplate.ELEVATION, plain))
        assertFalse(ShareLayout.available(ShareTemplate.RECORD, plain))
        assertTrue(ShareLayout.available(ShareTemplate.BOTTOM_STATS, plain))
    }

    companion object {
        private val five = listOf(
            ShareStat("Distance", "62,40", "km"),
            ShareStat("Elev Gain", "1.084", "m"),
            ShareStat("Time", "2h 31m", ""),
            ShareStat("Avg Power", "212", "W"),
            ShareStat("Speed", "24,8", "km/h"),
        )
        private val extreme = listOf(
            ShareStat("Distance", "1.234,56", "km"),
            ShareStat("Elev Gain", "12.480", "m"),
            ShareStat("Time", "52h 17m", ""),
            ShareStat("Avg Power", "1.212", "W"),
            ShareStat("Speed", "38,6", "km/h"),
        )
        private val record = ShareRecord("Rekor pribadi · 40 km", "1:12:08", "0:42 lebih cepat", 1)

        val inputs: Map<String, ShareInput> = mapOf(
            "five" to ShareInput("Gowes pagi Sentul", "Kamis, 15 Okt 2026 · 06.12", five, "1.162 m", "© OpenFreeMap © OpenStreetMap", record),
            "four" to ShareInput("Gowes pagi Sentul", "Kamis, 15 Okt 2026 · 06.12", five.filter { it.label != "Avg Power" }, "1.162 m", null, record),
            "extreme" to ShareInput(
                "Trans Jawa 1.234 km, Anyer sampai Banyuwangi tanpa menginap sama sekali",
                "Minggu, 27 Desember 2026 · 23.59",
                extreme,
                "12.480 m",
                "Citra © Esri, Maxar, Earthstar Geographics",
                ShareRecord("Rekor pribadi · Half marathon", "10:12:08", "1:02:42 lebih cepat dari rekor sebelumnya", 2),
            ),
            "record" to ShareInput("Gowes", "15 Okt", five, "1.162 m", "© OpenFreeMap", record),
        )

        @JvmStatic
        fun cases(): List<Arguments> = ShareTemplate.entries.flatMap { t ->
            ShareFormat.entries.flatMap { f -> listOf("five", "four", "extreme").map { Arguments.of(t, f, it) } }
        }
    }
}
