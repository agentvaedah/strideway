package id.web.agentvaedah.strideway.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.prefs.TtsProviderId
import id.web.agentvaedah.strideway.data.prefs.VoiceSettings
import id.web.agentvaedah.strideway.data.security.SecureKeyStore
import id.web.agentvaedah.strideway.di.AppScope
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks cues one at a time. The phone's own TTS is the default and the fallback; an API voice is
 * used only when chosen, keyed and reachable within [API_TIMEOUT_MS], so a dead zone never mutes a cue.
 * Music is ducked, not paused, while speaking.
 */
@Singleton
class VoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val scope: CoroutineScope,
    private val http: HttpClient,
    private val keys: SecureKeyStore,
    private val settings: SettingsRepository,
) {
    private val queue = Channel<String>(capacity = 8)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null

    init {
        scope.launch {
            for (text in queue) {
                runCatching { speakNow(text, settings.voice.first()) }
                    .onFailure { if (it is CancellationException) throw it }
            }
        }
    }

    /** Queues [text]; drops it when the queue is full rather than letting cues pile up behind a slow voice. */
    fun say(text: String) {
        queue.trySend(text)
    }

    /** Speaks immediately with the given settings, for the "test voice" button. Returns the engine used. */
    suspend fun test(v: VoiceSettings): String = speakNow("Strideway siap. 1 kilometer, waktu 3 menit 20 detik.", v)

    private suspend fun speakNow(text: String, v: VoiceSettings): String {
        audio?.requestAudioFocus(focus)
        try {
            if (v.provider != TtsProviderId.DEVICE) {
                val file = withTimeoutOrNull(API_TIMEOUT_MS) { fetchApiAudio(text, v) }
                if (file != null) {
                    try {
                        playFile(file)
                        return v.provider.label
                    } finally {
                        file.delete()
                    }
                }
            }
            speakDevice(text)
            return TtsProviderId.DEVICE.label
        } finally {
            audio?.abandonAudioFocusRequest(focus)
        }
    }

    private suspend fun fetchApiAudio(text: String, v: VoiceSettings): File? {
        val keyId = v.provider.keyId ?: return null
        val key = keys.get(keyId)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val response = when (v.provider) {
                TtsProviderId.OPENAI -> http.post("https://api.openai.com/v1/audio/speech") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $key")
                    setBody(buildJsonObject {
                        put("model", "gpt-4o-mini-tts")
                        put("voice", "alloy")
                        put("input", text)
                        put("response_format", "mp3")
                    })
                }
                TtsProviderId.ELEVENLABS -> http.post("https://api.elevenlabs.io/v1/text-to-speech/${v.elevenVoiceId}") {
                    contentType(ContentType.Application.Json)
                    header("xi-api-key", key)
                    header("Accept", "audio/mpeg")
                    setBody(buildJsonObject {
                        put("text", text)
                        put("model_id", "eleven_multilingual_v2")
                    })
                }
                TtsProviderId.DEVICE -> return null
            }
            if (!response.status.isSuccess()) return null
            val bytes = response.readRawBytes()
            withContext(Dispatchers.IO) {
                File(context.cacheDir, "cue-${UUID.randomUUID()}.mp3").apply { writeBytes(bytes) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun playFile(file: File) {
        val done = CompletableDeferred<Unit>()
        val player = withContext(Dispatchers.Main) {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(file.absolutePath)
                setOnCompletionListener { done.complete(Unit) }
                setOnErrorListener { _, _, _ -> done.complete(Unit); true }
                prepare()
                start()
            }
        }
        try {
            withTimeoutOrNull(MAX_UTTERANCE_MS) { done.await() }
        } finally {
            withContext(Dispatchers.Main) { player.release() }
        }
    }

    private suspend fun speakDevice(text: String) {
        val engine = deviceTts() ?: return
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id) done.complete(Unit) }
        })
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        withTimeoutOrNull(MAX_UTTERANCE_MS) { done.await() }
    }

    private suspend fun deviceTts(): TextToSpeech? {
        val ready = ttsReady ?: CompletableDeferred<Boolean>().also { d ->
            ttsReady = d
            tts = withContext(Dispatchers.Main) {
                TextToSpeech(context) { status -> d.complete(status == TextToSpeech.SUCCESS) }
            }
        }
        if (!ready.await()) {
            ttsReady = null
            tts?.shutdown()
            tts = null
            return null
        }
        val engine = tts ?: return null
        engine.setAudioAttributes(attributes)
        val indonesian = Locale.forLanguageTag("id-ID")
        if (engine.isLanguageAvailable(indonesian) >= TextToSpeech.LANG_AVAILABLE) engine.setLanguage(indonesian)
        return engine
    }

    private companion object {
        const val API_TIMEOUT_MS = 4_000L
        const val MAX_UTTERANCE_MS = 20_000L
    }
}
