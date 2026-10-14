package id.web.agentvaedah.strideway.data.remote.routing

import id.web.agentvaedah.strideway.core.geo.NominatimParser
import id.web.agentvaedah.strideway.core.geo.PlaceResult
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.route.BrouterParser
import id.web.agentvaedah.strideway.core.route.RouteOption
import id.web.agentvaedah.strideway.core.route.RouteOptions
import id.web.agentvaedah.strideway.core.model.TrackPoint
import id.web.agentvaedah.strideway.data.remote.StridewayJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class RoutingException(message: String) : Exception(message)

data class Place(val name: String, val pos: LatLon)

@Singleton
class RoutingClient @Inject constructor(private val http: HttpClient) {
    private val nominatimLock = Mutex()
    private var lastNominatimMs = 0L

    /** Nominatim usage policy allows one request per second; requests are serialised to respect it. */
    suspend fun geocode(query: String, near: LatLon? = null): Place? = nominatimLock.withLock {
        val wait = NOMINATIM_GAP_MS - (System.currentTimeMillis() - lastNominatimMs)
        if (wait > 0) delay(wait)
        try {
            val response = http.get("https://nominatim.openstreetmap.org/search") {
                parameter("q", query)
                parameter("format", "jsonv2")
                parameter("limit", 1)
                parameter("countrycodes", "id")
                parameter("accept-language", "id")
                if (near != null) {
                    // Soft bias toward the athlete's area without excluding other cities.
                    val d = 0.5
                    parameter("viewbox", String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", near.lon - d, near.lat + d, near.lon + d, near.lat - d))
                }
            }
            if (!response.status.isSuccess()) throw RoutingException("Pencarian tempat gagal (${response.status.value})")
            val first = StridewayJson.parseToJsonElement(response.bodyAsText()).jsonArray.firstOrNull()?.jsonObject ?: return@withLock null
            val lat = first["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@withLock null
            val lon = first["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@withLock null
            Place(first["display_name"]?.jsonPrimitive?.contentOrNull ?: query, LatLon(lat, lon))
        } catch (e: HttpRequestTimeoutException) {
            throw RoutingException("Pencarian tempat terlalu lama. Periksa koneksi.")
        } catch (e: IOException) {
            throw RoutingException("Tidak bisa menghubungi layanan pencarian tempat: ${e.message}")
        } finally {
            lastNominatimMs = System.currentTimeMillis()
        }
    }

    /** Up to [limit] places for a typed query, nearest area first. Same one-request-per-second rule as [geocode]. */
    suspend fun search(query: String, near: LatLon?, limit: Int = 8): List<PlaceResult> = nominatimLock.withLock {
        val wait = NOMINATIM_GAP_MS - (System.currentTimeMillis() - lastNominatimMs)
        if (wait > 0) delay(wait)
        try {
            val response = http.get("https://nominatim.openstreetmap.org/search") {
                parameter("q", query)
                parameter("format", "jsonv2")
                parameter("limit", limit)
                parameter("countrycodes", "id")
                parameter("accept-language", "id")
                if (near != null) {
                    val d = 0.5
                    parameter("viewbox", String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", near.lon - d, near.lat + d, near.lon + d, near.lat - d))
                    parameter("bounded", 0)
                }
            }
            if (!response.status.isSuccess()) throw RoutingException("Pencarian tempat gagal (${response.status.value})")
            NominatimParser.parse(response.bodyAsText())
        } catch (e: HttpRequestTimeoutException) {
            throw RoutingException("Pencarian tempat terlalu lama. Periksa koneksi.")
        } catch (e: IOException) {
            throw RoutingException("Tidak bisa menghubungi layanan pencarian tempat: ${e.message}")
        } finally {
            lastNominatimMs = System.currentTimeMillis()
        }
    }

    /** Road-following route through [points] in order, with elevation, from the public BRouter server. */
    suspend fun route(points: List<LatLon>, profile: String): List<TrackPoint> =
        option(points, profile, 0).points.map { TrackPoint(it.lat, it.lon, it.ele) }

    /**
     * Up to [count] different routes between the same points, with length, climb and surface mix.
     * Fetched one after another to stay gentle on the free public server; duplicates are dropped.
     */
    suspend fun alternatives(points: List<LatLon>, profile: String, count: Int = 3): List<RouteOption> {
        val out = ArrayList<RouteOption>()
        for (i in 0 until count.coerceIn(1, 4)) {
            val o = try {
                option(points, profile, i)
            } catch (e: RoutingException) {
                if (i == 0) throw e else break
            }
            out += o
        }
        return RouteOptions.distinct(out)
    }

    private suspend fun option(points: List<LatLon>, profile: String, alternative: Int): RouteOption {
        require(points.size >= 2) { "Butuh minimal dua titik" }
        val lonlats = points.joinToString("|") { String.format(Locale.US, "%.6f,%.6f", it.lon, it.lat) }
        try {
            val response = http.get("https://brouter.de/brouter") {
                parameter("lonlats", lonlats)
                parameter("profile", profile)
                parameter("alternativeidx", alternative)
                parameter("format", "geojson")
                // 2 = turn instructions as voicehints in the GeoJSON properties.
                parameter("turnInstructionMode", 2)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess() || !text.trimStart().startsWith("{")) {
                throw RoutingException("BRouter tidak menemukan rute: ${text.take(160).trim()}")
            }
            val o = BrouterParser.parse(text) ?: throw RoutingException("Balasan BRouter tidak berisi jalur")
            if (o.points.size < 2) throw RoutingException("Balasan BRouter tidak berisi jalur")
            return o
        } catch (e: HttpRequestTimeoutException) {
            throw RoutingException("Perhitungan rute terlalu lama. Coba rute yang lebih pendek.")
        } catch (e: IOException) {
            throw RoutingException("Tidak bisa menghubungi BRouter: ${e.message}")
        }
    }

    private companion object {
        const val NOMINATIM_GAP_MS = 1_100L
    }
}
