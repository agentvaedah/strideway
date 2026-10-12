package id.web.agentvaedah.strideway.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.coach.CoachContext
import id.web.agentvaedah.strideway.core.coach.RecentActivity
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import id.web.agentvaedah.strideway.data.db.ChatMessageEntity
import id.web.agentvaedah.strideway.data.db.ChatRole
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.remote.llm.LlmClient
import id.web.agentvaedah.strideway.data.remote.llm.LlmMessage
import id.web.agentvaedah.strideway.data.remote.llm.LlmProviderId
import id.web.agentvaedah.strideway.data.remote.llm.LlmResult
import id.web.agentvaedah.strideway.data.remote.llm.LlmSettings
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import id.web.agentvaedah.strideway.data.repo.ChatRepository
import id.web.agentvaedah.strideway.data.security.SecureKeyStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CoachViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val llm: LlmClient,
    private val settings: SettingsRepository,
    private val activities: ActivityRepository,
    keys: SecureKeyStore,
) : ViewModel() {
    val messages: StateFlow<List<ChatMessageEntity>?> = chat.messages().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val llmSettings: StateFlow<LlmSettings> = settings.llm.stateIn(viewModelScope, SharingStarted.Eagerly, LlmSettings())

    val hasKey: StateFlow<Boolean> = settings.llm
        .flatMapLatest { keys.hasKey(it.provider) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _sending.value) return
        _sending.value = true
        viewModelScope.launch {
            try {
                ask(trimmed)
            } finally {
                _sending.value = false
            }
        }
    }

    private suspend fun ask(trimmed: String) {
        val cfg = settings.llm.first()
        chat.add(ChatRole.USER, trimmed, null)
        // Error bubbles are shown to the athlete but never sent back to the model.
        val history = chat.history()
            .filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
            .takeLast(HISTORY_LIMIT)
            .map { LlmMessage(it.role, it.content) }
            .dropWhile { it.role != ChatRole.USER }
        when (val r = llm.complete(cfg, systemPrompt(), history)) {
            is LlmResult.Ok -> chat.add(ChatRole.ASSISTANT, r.text, providerLabel(cfg))
            is LlmResult.Failure -> chat.add(ChatRole.ERROR, r.reason, providerLabel(cfg))
        }
    }

    fun clear() = viewModelScope.launch { chat.clear() }

    private fun providerLabel(cfg: LlmSettings) = "${cfg.provider.label} · ${cfg.model}"

    private suspend fun systemPrompt(): String {
        val zone = ZoneId.systemDefault()
        val athlete = settings.athlete.first()
        val list = activities.activities().first()
        val today = LocalDate.now(zone).toEpochDay()
        val loads = list.map { TrainingLoad.DailyLoad(Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().toEpochDay(), it.trimp) }
        val fitness = if (list.isEmpty()) null else TrainingLoad.series(loads, today, today).lastOrNull()
        val recent = list.take(5).map { RecentActivity(it.sport, it.startMs, it.distanceM, it.movingMs, it.elevationGainM, it.avgHr) }
        return CoachContext.systemPrompt(athlete, fitness, recent, zone)
    }

    private companion object {
        const val HISTORY_LIMIT = 20
    }
}

@HiltViewModel
class CoachKeysViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val keys: SecureKeyStore,
    private val llm: LlmClient,
) : ViewModel() {
    val active: StateFlow<LlmSettings?> = settings.llm.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _editing = MutableStateFlow<LlmSettings?>(null)
    val editing: StateFlow<LlmSettings?> = _editing.asStateFlow()

    private val _hasKey = MutableStateFlow(false)
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    private val _status = MutableStateFlow<KeyStatus>(KeyStatus.Idle)
    val status: StateFlow<KeyStatus> = _status.asStateFlow()

    init {
        viewModelScope.launch { select(settings.llm.first().provider) }
    }

    fun select(provider: LlmProviderId) {
        viewModelScope.launch {
            _editing.value = settings.llmFor(provider)
            _hasKey.value = keys.hasKey(provider).first()
            _status.value = KeyStatus.Idle
        }
    }

    fun edit(transform: (LlmSettings) -> LlmSettings) {
        _editing.value = _editing.value?.let(transform)
        _status.value = KeyStatus.Idle
    }

    /** Saves model, base URL and (if typed) a new key, then makes this provider the active one. */
    fun save(newKey: String) {
        val cfg = _editing.value ?: return
        viewModelScope.launch {
            if (newKey.isNotBlank()) keys.put(cfg.provider, newKey)
            settings.saveLlm(cfg)
            _hasKey.value = keys.hasKey(cfg.provider).first()
            _status.value = KeyStatus.Saved
        }
    }

    fun test(newKey: String) {
        val cfg = _editing.value ?: return
        _status.value = KeyStatus.Testing
        viewModelScope.launch {
            if (newKey.isNotBlank()) {
                keys.put(cfg.provider, newKey)
                _hasKey.value = true
            }
            _status.value = when (val r = llm.test(cfg)) {
                is LlmResult.Ok -> KeyStatus.TestOk
                is LlmResult.Failure -> KeyStatus.TestFailed(r.reason)
            }
        }
    }

    fun removeKey() {
        val cfg = _editing.value ?: return
        viewModelScope.launch {
            keys.remove(cfg.provider)
            _hasKey.value = false
            _status.value = KeyStatus.Removed
        }
    }
}

sealed interface KeyStatus {
    data object Idle : KeyStatus
    data object Saved : KeyStatus
    data object Removed : KeyStatus
    data object Testing : KeyStatus
    data object TestOk : KeyStatus
    data class TestFailed(val reason: String) : KeyStatus
}
