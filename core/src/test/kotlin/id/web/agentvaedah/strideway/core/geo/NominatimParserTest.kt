package id.web.agentvaedah.strideway.core.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NominatimParserTest {
    @Test
    fun parsesResultsAndSkipsBroken() {
        val json = """
            [{"lat":"-6.1754","lon":"106.8272","name":"Monas","display_name":"Monas, Gambir, Jakarta","type":"monument"},
             {"lat":"bad","lon":"106.8","display_name":"Rusak"},
             {"lat":"-6.6","lon":"106.8","display_name":"Kebun Raya Bogor, Bogor","type":"garden"}]
        """.trimIndent()
        val r = NominatimParser.parse(json)
        assertEquals(2, r.size)
        assertEquals("Monas", r[0].name)
        assertEquals(-6.1754, r[0].lat, 1e-9)
        assertEquals("Kebun Raya Bogor", r[1].name)
    }

    @Test
    fun notAnArrayIsEmpty() {
        assertEquals(0, NominatimParser.parse("{}").size)
    }
}
