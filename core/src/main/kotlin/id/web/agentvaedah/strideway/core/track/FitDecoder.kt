package id.web.agentvaedah.strideway.core.track

import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.model.Track
import id.web.agentvaedah.strideway.core.model.TrackPoint

/**
 * Garmin FIT decoder covering what an endurance import needs: `record` (20) points and
 * `sport` (12) / `session` (18) for the activity type. Developer fields are skipped.
 */
object FitDecoder {
    private const val FIT_EPOCH_OFFSET_S = 631_065_600L
    private const val SEMICIRCLE_TO_DEG = 180.0 / 2147483648.0

    private const val MSG_SPORT = 12
    private const val MSG_SESSION = 18
    private const val MSG_RECORD = 20
    private const val FIELD_TIMESTAMP = 253

    private class FieldDef(val num: Int, val size: Int, val baseType: Int)

    private class Definition(
        val global: Int,
        val bigEndian: Boolean,
        val fields: List<FieldDef>,
        val devBytes: Int,
    )

    fun decode(bytes: ByteArray): Track {
        if (bytes.size < 12) throw TrackParseException("File FIT terlalu pendek")
        val headerSize = bytes[0].toInt() and 0xFF
        if (headerSize < 12 || bytes.size < headerSize) throw TrackParseException("Header FIT tidak valid")
        if (String(bytes, 8, 4, Charsets.US_ASCII) != ".FIT") throw TrackParseException("Bukan file FIT")
        val dataSize = readUInt(bytes, 4, 4, bigEndian = false).toInt()
        val end = (headerSize + dataSize).coerceAtMost(bytes.size)

        val defs = HashMap<Int, Definition>()
        val points = ArrayList<TrackPoint>()
        var sport: Sport? = null
        var lastTimestamp = 0L
        var i = headerSize

        while (i < end) {
            val header = bytes[i].toInt() and 0xFF
            i++
            if ((header and 0x80) != 0) {
                val local = (header shr 5) and 0x03
                val offset = header and 0x1F
                var ts = (lastTimestamp and 0x1F.inv().toLong()) + offset
                if (offset < (lastTimestamp and 0x1F)) ts += 0x20
                lastTimestamp = ts
                val def = defs[local] ?: throw TrackParseException("Pesan FIT tanpa definisi (lokal $local)")
                val values = readValues(bytes, i, def)
                i += dataLength(def)
                if (def.global == MSG_RECORD) toPoint(values, ts)?.let(points::add)
                continue
            }
            val local = header and 0x0F
            if ((header and 0x40) != 0) {
                val hasDev = (header and 0x20) != 0
                if (i + 5 > end) break
                val bigEndian = bytes[i + 1].toInt() != 0
                val global = readUInt(bytes, i + 2, 2, bigEndian).toInt()
                val count = bytes[i + 4].toInt() and 0xFF
                i += 5
                val fields = ArrayList<FieldDef>(count)
                repeat(count) {
                    fields += FieldDef(bytes[i].toInt() and 0xFF, bytes[i + 1].toInt() and 0xFF, bytes[i + 2].toInt() and 0xFF)
                    i += 3
                }
                var devBytes = 0
                if (hasDev) {
                    val devCount = bytes[i].toInt() and 0xFF
                    i++
                    repeat(devCount) {
                        devBytes += bytes[i + 1].toInt() and 0xFF
                        i += 3
                    }
                }
                defs[local] = Definition(global, bigEndian, fields, devBytes)
            } else {
                val def = defs[local] ?: throw TrackParseException("Pesan FIT tanpa definisi (lokal $local)")
                val values = readValues(bytes, i, def)
                i += dataLength(def)
                values[FIELD_TIMESTAMP]?.let { lastTimestamp = it }
                when (def.global) {
                    MSG_RECORD -> toPoint(values, values[FIELD_TIMESTAMP] ?: lastTimestamp)?.let(points::add)
                    MSG_SPORT -> sport = sport ?: sportOf(values[0])
                    MSG_SESSION -> sport = sport ?: sportOf(values[5])
                }
            }
        }
        if (points.isEmpty()) throw TrackParseException("FIT tidak berisi titik GPS")
        return Track(null, sport, points)
    }

    private fun dataLength(def: Definition) = def.fields.sumOf { it.size } + def.devBytes

    /** Field number to raw value; invalid (all-ones / max) values are left out. */
    private fun readValues(bytes: ByteArray, start: Int, def: Definition): Map<Int, Long> {
        if (start + dataLength(def) > bytes.size) throw TrackParseException("Data FIT terpotong")
        val out = HashMap<Int, Long>()
        var i = start
        for (f in def.fields) {
            if (f.size in 1..4) {
                val raw = readUInt(bytes, i, f.size, def.bigEndian)
                val signed = (f.baseType and 0x1F) in setOf(0x01, 0x03, 0x05)
                if (!isInvalid(raw, f.size, signed)) {
                    out[f.num] = if (signed) signExtend(raw, f.size) else raw
                }
            }
            i += f.size
        }
        return out
    }

    private fun isInvalid(raw: Long, size: Int, signed: Boolean): Boolean {
        val all = (1L shl (8 * size)) - 1
        return if (signed) raw == (all shr 1) else raw == all
    }

    private fun signExtend(raw: Long, size: Int): Long {
        val bits = 8 * size
        return (raw shl (64 - bits)) shr (64 - bits)
    }

    private fun readUInt(bytes: ByteArray, start: Int, size: Int, bigEndian: Boolean): Long {
        var v = 0L
        for (k in 0 until size) {
            val b = bytes[start + k].toLong() and 0xFF
            v = if (bigEndian) (v shl 8) or b else v or (b shl (8 * k))
        }
        return v
    }

    private fun toPoint(v: Map<Int, Long>, fitTimestamp: Long): TrackPoint? {
        val lat = v[0] ?: return null
        val lon = v[1] ?: return null
        val altitude = v[78] ?: v[2]
        return TrackPoint(
            lat = lat * SEMICIRCLE_TO_DEG,
            lon = lon * SEMICIRCLE_TO_DEG,
            ele = altitude?.let { it / 5.0 - 500.0 },
            timeMs = if (fitTimestamp > 0) (fitTimestamp + FIT_EPOCH_OFFSET_S) * 1000 else null,
            hr = v[3]?.toInt(),
            cadence = v[4]?.toInt(),
            power = v[7]?.toInt(),
        )
    }

    private fun sportOf(value: Long?): Sport? = when (value) {
        1L, 11L -> Sport.RUN
        2L -> Sport.RIDE
        else -> null
    }
}
