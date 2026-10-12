package id.web.agentvaedah.strideway.data.remote.llm

import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.remote.StridewayJson
import id.web.agentvaedah.strideway.data.security.SecureKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BYOK chat client. The request goes straight from the phone to the provider with the user's
 * own key; Strideway has no server in the middle, so quota and data stay with the user.
 */
@Singleton
class LlmClient @Inject constructor(
    private val http: HttpClient,
    private val keys: SecureKeyStore,
    private val settings: SettingsRepository,
) {
    suspend fun complete(system: String, messages: List<LlmMessage>, maxTokens: Int = DEFAULT_MAX_TOKENS): LlmResult =
        complete(settings.llm.first(), system, messages, maxTokens)

    suspend fun complete(cfg: LlmSettings, system: String, messages: List<LlmMessage>, maxTokens: Int = DEFAULT_MAX_TOKENS): LlmResult {
        val key = keys.get(cfg.provider)
        if (cfg.provider.keyRequired && key.isNullOrBlank()) {
            return LlmResult.Failure("Belum ada API key ${cfg.provider.label}. Tambahkan di Coach > API key.")
        }
        if (messages.isEmpty()) return LlmResult.Failure("Pesan kosong")
        return try {
            when (cfg.provider) {
                LlmProviderId.ANTHROPIC -> anthropic(cfg, key!!, system, messages, maxTokens)
                LlmProviderId.GEMINI -> gemini(cfg, key!!, system, messages, maxTokens)
                LlmProviderId.OPENAI, LlmProviderId.GROQ, LlmProviderId.OLLAMA -> openAiCompatible(cfg, key, system, messages, maxTokens)
            }
        } catch (e: HttpRequestTimeoutException) {
            LlmResult.Failure("Server ${cfg.provider.label} tidak merespons. Coba lagi sebentar.")
        } catch (e: UnknownHostException) {
            LlmResult.Failure("Tidak ada koneksi internet atau alamat server salah (${cfg.baseUrl}).")
        } catch (e: IOException) {
            LlmResult.Failure("Koneksi ke ${cfg.provider.label} gagal: ${e.message}")
        } catch (e: SerializationException) {
            LlmResult.Failure("Balasan ${cfg.provider.label} tidak bisa dibaca.")
        } catch (e: IllegalArgumentException) {
            LlmResult.Failure("Balasan ${cfg.provider.label} tidak bisa dibaca.")
        }
    }

    /** Small round trip to confirm the key, model and base URL work before the user relies on them. */
    suspend fun test(cfg: LlmSettings): LlmResult =
        complete(cfg, "Balas hanya dengan kata: OK", listOf(LlmMessage("user", "Tes koneksi")), maxTokens = 4_000)

    private suspend fun openAiCompatible(cfg: LlmSettings, key: String?, system: String, messages: List<LlmMessage>, maxTokens: Int): LlmResult {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                messages.forEach { m -> addJsonObject { put("role", m.role); put("content", m.content) } }
            }
        }
        val response = http.post("${cfg.baseUrl}/chat/completions") {
            contentType(ContentType.Application.Json)
            if (!key.isNullOrBlank()) header("Authorization", "Bearer $key")
            setBody(body)
        }
        val json = response.json() ?: return failure(cfg, response)
        val text = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        return if (text.isNullOrBlank()) LlmResult.Failure("${cfg.provider.label} mengirim jawaban kosong.") else LlmResult.Ok(text.trim())
    }

    private suspend fun gemini(cfg: LlmSettings, key: String, system: String, messages: List<LlmMessage>, maxTokens: Int): LlmResult {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
            putJsonArray("contents") {
                messages.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == "assistant") "model" else "user")
                        putJsonArray("parts") { addJsonObject { put("text", m.content) } }
                    }
                }
            }
            putJsonObject("generationConfig") { put("maxOutputTokens", maxTokens.coerceAtMost(GEMINI_MAX_OUTPUT)) }
        }
        val response = http.post("${cfg.baseUrl}/models/${cfg.model}:generateContent") {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", key)
            setBody(body)
        }
        val json = response.json() ?: return failure(cfg, response)
        val candidate = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        val text = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
        if (text.isNullOrBlank()) {
            val reason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
                ?: json["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
            return LlmResult.Failure("Gemini tidak memberi jawaban" + (reason?.let { " ($it)" } ?: "."))
        }
        return LlmResult.Ok(text.trim())
    }

    private suspend fun anthropic(cfg: LlmSettings, key: String, system: String, messages: List<LlmMessage>, maxTokens: Int): LlmResult {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("max_tokens", maxTokens)
            put("system", system)
            // A request declined by safety classifiers is re-run server-side on a recommended model
            // instead of returning an empty refusal; only some models accept the parameter.
            if (supportsFallback(cfg.model)) put("fallbacks", "default")
            putJsonArray("messages") {
                messages.forEach { m -> addJsonObject { put("role", m.role); put("content", m.content) } }
            }
        }
        val response = http.post("${cfg.baseUrl}/messages") {
            contentType(ContentType.Application.Json)
            header("x-api-key", key)
            header("anthropic-version", "2023-06-01")
            if (supportsFallback(cfg.model)) header("anthropic-beta", "server-side-fallback-2026-07-01")
            setBody(body)
        }
        val json = response.json() ?: return failure(cfg, response)
        if (json["stop_reason"]?.jsonPrimitive?.contentOrNull == "refusal") {
            return LlmResult.Failure("Claude menolak menjawab pertanyaan ini. Coba ubah kalimatnya.")
        }
        val text = json["content"]?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
        return if (text.isNullOrBlank()) LlmResult.Failure("Claude mengirim jawaban kosong.") else LlmResult.Ok(text.trim())
    }

    private fun supportsFallback(model: String) =
        FALLBACK_MODEL_PREFIXES.any { model.startsWith(it) }

    private suspend fun HttpResponse.json(): JsonObject? {
        if (!status.isSuccess()) return null
        return StridewayJson.parseToJsonElement(bodyAsText()).jsonObject
    }

    private suspend fun failure(cfg: LlmSettings, response: HttpResponse): LlmResult.Failure {
        val detail = runCatching { extractError(StridewayJson.parseToJsonElement(response.bodyAsText())) }.getOrNull()
        val base = when (response.status.value) {
            400 -> "Permintaan ditolak (400). Periksa nama model \"${cfg.model}\"."
            401, 403 -> "API key ${cfg.provider.label} ditolak. Periksa atau ganti key di Coach > API key."
            404 -> "Model atau alamat tidak ditemukan (404). Periksa nama model dan base URL."
            429 -> "Kuota ${cfg.provider.label} habis atau terkena batas laju. Tunggu sebentar atau cek tagihan akunmu."
            in 500..599 -> "Server ${cfg.provider.label} sedang bermasalah (${response.status.value}). Coba lagi nanti."
            else -> "Permintaan gagal (${response.status.value})."
        }
        return LlmResult.Failure(if (detail.isNullOrBlank()) base else "$base\n$detail")
    }

    private fun extractError(e: JsonElement): String? {
        val obj = (e as? JsonObject) ?: (e as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val err = obj["error"]
        return when (err) {
            is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> err.contentOrNull
            else -> obj["message"]?.jsonPrimitive?.contentOrNull
        }?.take(300)
    }

    private companion object {
        // Thinking models spend part of max_tokens on reasoning; too low a cap truncates the answer.
        const val DEFAULT_MAX_TOKENS = 16_000
        const val GEMINI_MAX_OUTPUT = 8_192
        val FALLBACK_MODEL_PREFIXES = listOf("claude-opus-5", "claude-fable-5", "claude-sonnet-5-5")
    }
}
