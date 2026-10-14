package id.web.agentvaedah.strideway.core.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class PlaceResult(val name: String, val displayName: String, val lat: Double, val lon: Double, val type: String?)

object NominatimParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Nominatim /search with format=json. Entries without valid coordinates are skipped. */
    fun parse(text: String): List<PlaceResult> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
        return root.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
            val lat = s("lat")?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = s("lon")?.toDoubleOrNull() ?: return@mapNotNull null
            val display = s("display_name").orEmpty()
            val name = s("name")?.takeIf { it.isNotBlank() } ?: display.substringBefore(',').trim()
            PlaceResult(name, display, lat, lon, s("type"))
        }
    }
}
