package id.web.agentvaedah.strideway.core.route

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** What the LLM extracts from a free-text route request; routing itself happens in BRouter. */
@Serializable
data class RouteIntent(
    val start: String,
    val waypoints: List<String> = emptyList(),
    val end: String? = null,
    val sport: String = "road",
    val surface: String = "paved",
    val elevation: String = "any",
    @SerialName("target_km") val targetKm: Double? = null,
    val loop: Boolean = false,
)

class RouteIntentException(message: String) : Exception(message)

object RouteIntents {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val SYSTEM_PROMPT: String = """
        Kamu mengubah permintaan rute bersepeda atau lari menjadi JSON. Balas HANYA dengan satu objek JSON tanpa teks lain.
        Skema:
        {"start": string, "waypoints": [string], "end": string|null, "sport": "road"|"gravel"|"mtb"|"run",
         "surface": "paved"|"mixed"|"unpaved", "elevation": "flat"|"rolling"|"hilly"|"any", "target_km": number|null, "loop": boolean}
        Aturan:
        - Nama tempat ditulis lengkap dengan kota agar bisa dicari di peta, contoh "Monas, Jakarta".
        - Urutkan waypoints sesuai urutan yang disebut pengguna.
        - Jika pengguna tidak menyebut tujuan akhir dan meminta jarak tertentu, isi loop=true dan end=null.
        - "aspal mulus" berarti surface=paved. "landai" atau "datar" berarti elevation=flat.
    """.trimIndent()

    fun parse(llmText: String): RouteIntent {
        val start = llmText.indexOf('{')
        val end = llmText.lastIndexOf('}')
        if (start < 0 || end <= start) throw RouteIntentException("Jawaban AI tidak berisi JSON rute")
        val intent = try {
            json.decodeFromString(RouteIntent.serializer(), llmText.substring(start, end + 1))
        } catch (e: SerializationException) {
            throw RouteIntentException("JSON rute dari AI tidak valid: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw RouteIntentException("JSON rute dari AI tidak valid: ${e.message}")
        }
        if (intent.start.isBlank()) throw RouteIntentException("AI tidak menemukan titik awal rute")
        return intent.copy(waypoints = intent.waypoints.filter { it.isNotBlank() }.take(MAX_WAYPOINTS))
    }

    /** BRouter profile names available on the public brouter.de instance. */
    fun brouterProfile(intent: RouteIntent): String = when (intent.sport) {
        "run" -> "hiking-beta"
        "mtb" -> "mtb"
        "gravel" -> "trekking"
        else -> if (intent.surface == "unpaved") "trekking" else "fastbike"
    }

    const val MAX_WAYPOINTS = 8
}
