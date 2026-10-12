package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class MiniXmlTest {
    @Test
    fun parsesNestedElementsAttributesAndEntities() {
        val root = MiniXml.parse(
            """<?xml version="1.0"?><!-- c --><a:root x="1" b:y='2 &amp; 3'><item>A &lt; B</item><empty/><![CDATA[<raw>]]></a:root>""",
        )
        assertEquals("root", root.name)
        assertEquals("1", root.attrs["x"])
        assertEquals("2 & 3", root.attrs["y"])
        assertEquals("A < B", root.child("item")!!.text)
        assertEquals("<raw>", root.text)
        assertEquals(2, root.children.size)
    }

    @Test
    fun rejectsMismatchedTags() {
        assertThrows<TrackParseException> { MiniXml.parse("<a><b></a></b>") }
    }
}

class GpxTest {
    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1"
             xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
          <trk><name>Audax BRM 200</name><type>cycling</type><trkseg>
            <trkpt lat="-6.1754" lon="106.8272"><ele>12.5</ele><time>2026-10-01T22:00:00Z</time>
              <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>132</gpxtpx:hr><gpxtpx:cad>85</gpxtpx:cad></gpxtpx:TrackPointExtension></extensions>
            </trkpt>
            <trkpt lat="-6.1950" lon="106.8230"><ele>10.0</ele><time>2026-10-01T22:05:00Z</time></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    @Test
    fun parsesTrackPointsAndExtensions() {
        val t = Gpx.parse(sample)
        assertEquals("Audax BRM 200", t.name)
        assertEquals(Sport.RIDE, t.sport)
        assertEquals(2, t.points.size)
        val p = t.points[0]
        assertEquals(-6.1754, p.lat, 1e-9)
        assertEquals(12.5, p.ele)
        assertEquals(132, p.hr)
        assertEquals(85, p.cadence)
        assertEquals(1_790_892_000_000L, p.timeMs)
        assertNull(t.points[1].hr)
    }

    @Test
    fun fallsBackToRoutePoints() {
        val t = Gpx.parse("""<gpx><rte><name>R</name><rtept lat="1" lon="2"/><rtept lat="1.1" lon="2"/></rte></gpx>""")
        assertEquals("R", t.name)
        assertEquals(2, t.points.size)
    }

    @Test
    fun writeThenParseRoundTrip() {
        val original = Track("Pagi & santai", Sport.RUN, listOf(
            TrackPoint(-6.2, 106.8, 20.0, 1_000_000L, hr = 140, cadence = 170),
            TrackPoint(-6.21, 106.81, 25.0, 1_060_000L),
        ))
        val back = Gpx.parse(Gpx.write(original))
        assertEquals("Pagi & santai", back.name)
        assertEquals(Sport.RUN, back.sport)
        assertEquals(original.points.map { it.lat }, back.points.map { it.lat })
        assertEquals(140, back.points[0].hr)
        assertEquals(1_060_000L, back.points[1].timeMs)
    }

    @Test
    fun emptyGpxIsAnError() {
        assertThrows<TrackParseException> { Gpx.parse("<gpx></gpx>") }
    }
}

class TcxTest {
    @Test
    fun parsesActivity() {
        val xml = """
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities><Activity Sport="Running"><Lap><Track>
                <Trackpoint><Time>2026-10-01T00:00:00Z</Time><Position><LatitudeDegrees>-6.2</LatitudeDegrees>
                  <LongitudeDegrees>106.8</LongitudeDegrees></Position><AltitudeMeters>5</AltitudeMeters>
                  <HeartRateBpm><Value>150</Value></HeartRateBpm></Trackpoint>
                <Trackpoint><Time>2026-10-01T00:00:01Z</Time></Trackpoint>
              </Track></Lap></Activity></Activities>
            </TrainingCenterDatabase>
        """.trimIndent()
        val t = Tcx.parse(xml)
        assertEquals(Sport.RUN, t.sport)
        assertEquals(1, t.points.size)
        assertEquals(150, t.points[0].hr)
        assertEquals(5.0, t.points[0].ele)
    }

    @Test
    fun writeThenParseRoundTrip() {
        val track = Track("Sore", Sport.RIDE, listOf(
            TrackPoint(-6.2, 106.8, 10.0, 0L, hr = 120, cadence = 88, power = 210),
            TrackPoint(-6.201, 106.8, 11.0, 2_000L),
        ))
        val back = Tcx.parse(Tcx.write(track, Sport.RIDE))
        assertEquals(Sport.RIDE, back.sport)
        assertEquals(2, back.points.size)
        assertEquals(88, back.points[0].cadence)
        assertEquals(210, back.points[0].power)
    }
}

class FitDecoderTest {
    private val semicircle = 2147483648.0 / 180.0

    private class Le(val out: ByteArrayOutputStream = ByteArrayOutputStream()) {
        fun u8(v: Int) = apply { out.write(v and 0xFF) }
        fun u16(v: Int) = apply { u8(v); u8(v shr 8) }
        fun u32(v: Long) = apply { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
    }

    private fun fitFile(body: ByteArray): ByteArray {
        val h = Le().u8(14).u8(0x20).u16(2140).u32(body.size.toLong())
        h.out.write(".FIT".toByteArray(Charsets.US_ASCII))
        h.u16(0)
        h.out.write(body)
        h.u16(0)
        return h.out.toByteArray()
    }

    private fun recordDefinition(b: Le) {
        b.u8(0x40).u8(0).u8(0).u16(20).u8(5)
        b.u8(253).u8(4).u8(0x86) // timestamp uint32
        b.u8(0).u8(4).u8(0x85)   // position_lat sint32
        b.u8(1).u8(4).u8(0x85)   // position_long sint32
        b.u8(2).u8(2).u8(0x84)   // altitude uint16, scale 5 offset 500
        b.u8(3).u8(1).u8(0x02)   // heart_rate uint8
    }

    private fun record(b: Le, ts: Long, lat: Double, lon: Double, altM: Double, hr: Int) {
        b.u8(0x00).u32(ts)
        b.u32((lat * semicircle).roundToInt().toLong() and 0xFFFFFFFFL)
        b.u32((lon * semicircle).roundToInt().toLong() and 0xFFFFFFFFL)
        b.u16(((altM + 500) * 5).roundToInt())
        b.u8(hr)
    }

    @Test
    fun decodesRecordsAndSport() {
        val b = Le()
        b.u8(0x41).u8(0).u8(0).u16(12).u8(1).u8(0).u8(1).u8(0x00)
        b.u8(0x01).u8(2)
        recordDefinition(b)
        record(b, 1_000_000_000L, -6.2, 106.8, 25.0, 140)
        record(b, 1_000_000_001L, -6.2001, 106.8001, 26.0, 0xFF)

        val t = FitDecoder.decode(fitFile(b.out.toByteArray()))
        assertEquals(Sport.RIDE, t.sport)
        assertEquals(2, t.points.size)
        assertEquals(-6.2, t.points[0].lat, 1e-6)
        assertEquals(106.8, t.points[0].lon, 1e-6)
        assertEquals(25.0, t.points[0].ele!!, 0.2)
        assertEquals(140, t.points[0].hr)
        assertNull(t.points[1].hr)
        assertEquals((1_000_000_000L + 631_065_600L) * 1000, t.points[0].timeMs)
    }

    @Test
    fun compressedTimestampHeader() {
        val b = Le()
        recordDefinition(b)
        record(b, 1_000_000_000L, -6.2, 106.8, 25.0, 140)
        // Compressed header: local type 0, time offset = (lastTs & 0x1F) + 3.
        val offset = ((1_000_000_000L and 0x1F) + 3).toInt() and 0x1F
        b.u8(0x80 or offset)
        b.u32(0) // timestamp field is still present in the definition
        b.u32((-6.21 * semicircle).roundToInt().toLong() and 0xFFFFFFFFL)
        b.u32((106.81 * semicircle).roundToInt().toLong() and 0xFFFFFFFFL)
        b.u16(((30.0 + 500) * 5).roundToInt())
        b.u8(150)

        val t = FitDecoder.decode(fitFile(b.out.toByteArray()))
        assertEquals(2, t.points.size)
        assertEquals((1_000_000_003L + 631_065_600L) * 1000, t.points[1].timeMs)
    }

    @Test
    fun rejectsNonFit() {
        assertThrows<TrackParseException> { FitDecoder.decode(ByteArray(20)) }
    }
}

class TrackFormatsTest {
    @Test
    fun sniffsGpxWithoutExtension() {
        val t = TrackFormats.parse("rute", """<gpx><trk><trkseg><trkpt lat="1" lon="2"/></trkseg></trk></gpx>""".toByteArray())
        assertEquals("rute", t.name)
        assertEquals(1, t.points.size)
    }

    @Test
    fun unknownFormat() {
        assertThrows<TrackParseException> { TrackFormats.parse("x.kml", "<kml/>".toByteArray()) }
    }

    @Test
    fun downsampleKeepsEnds() {
        val d = TrackStats.downsample((0..99).toList(), 10)
        assertEquals(10, d.size)
        assertEquals(0, d.first())
        assertEquals(99, d.last())
    }
}
