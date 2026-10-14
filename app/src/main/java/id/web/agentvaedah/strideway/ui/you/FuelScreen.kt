package id.web.agentvaedah.strideway.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.fuel.FuelPlan
import id.web.agentvaedah.strideway.core.fuel.FuelPlanner
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.data.location.GpsSource
import id.web.agentvaedah.strideway.data.prefs.FuelSettings
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.remote.llm.LlmClient
import id.web.agentvaedah.strideway.data.remote.llm.LlmMessage
import id.web.agentvaedah.strideway.data.remote.llm.LlmResult
import id.web.agentvaedah.strideway.data.remote.weather.WeatherRepository
import id.web.agentvaedah.strideway.data.remote.weather.WeatherState
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.components.StatPair
import id.web.agentvaedah.strideway.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface AdviceState {
    data object Idle : AdviceState
    data object Loading : AdviceState
    data class Ok(val text: String) : AdviceState
    data class Failed(val reason: String) : AdviceState
}

@HiltViewModel
class FuelViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val weather: WeatherRepository,
    private val gps: GpsSource,
    private val llm: LlmClient,
) : ViewModel() {
    val fuel: StateFlow<FuelSettings> = settings.fuel.stateIn(viewModelScope, SharingStarted.Eagerly, FuelSettings())

    private val _sport = MutableStateFlow(Sport.RIDE)
    val sport: StateFlow<Sport> = _sport.asStateFlow()
    private val _duration = MutableStateFlow(120)
    val durationMin: StateFlow<Int> = _duration.asStateFlow()

    val tempC: StateFlow<Double?> = weather.state
        .map { (it as? WeatherState.Ready)?.report?.current?.tempC }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val plan: StateFlow<FuelPlan?> = combine(_sport, _duration, tempC, settings.athlete) { s, d, t, a ->
        FuelPlanner.plan(s, d, t, a.weightKg)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _advice = MutableStateFlow<AdviceState>(AdviceState.Idle)
    val advice: StateFlow<AdviceState> = _advice.asStateFlow()
    private var adviceJob: Job? = null

    init {
        gps.lastKnown()?.let { p -> viewModelScope.launch { weather.refresh(p.lat, p.lon) } }
    }

    fun setSport(s: Sport) { _sport.value = s }
    fun setDuration(min: Int) { _duration.value = min }

    fun save(f: FuelSettings) { viewModelScope.launch { settings.saveFuel(f) } }

    /** Asks the user's own LLM for a plan grounded in the computed numbers, so it refines rather than invents. */
    fun askAi() {
        val p = plan.value ?: return
        adviceJob?.cancel()
        _advice.value = AdviceState.Loading
        adviceJob = viewModelScope.launch {
            val a = settings.athlete.first()
            val t = tempC.value
            val sportName = if (_sport.value == Sport.RIDE) "bersepeda" else "lari"
            val prompt = buildString {
                append("Saya akan $sportName selama ${_duration.value} menit. ")
                append("Berat badan ${a.weightKg.roundToInt()} kg, usia ${a.age}. ")
                append(if (t != null) "Suhu sekarang ${t.roundToInt()}°C. " else "Suhu belum diketahui. ")
                append("Perhitungan dasar aplikasi: cairan ${p.fluidMlPerHour} ml/jam, karbohidrat ${p.carbGramsPerHour} g/jam, ")
                append("minum tiap ${p.drinkEveryMin} menit, elektrolit ${if (p.electrolytes) "perlu" else "opsional"}. ")
                append("Buat rencana singkat: sebelum, selama, dan sesudah latihan, dengan contoh makanan dan minuman yang mudah didapat di Indonesia. ")
                append("Maksimal 8 poin, bahasa Indonesia, tanpa pembuka.")
            }
            val system = "Kamu pelatih ketahanan yang memberi saran hidrasi dan nutrisi praktis. " +
                "Jangan memberi saran medis; sarankan konsultasi dokter bila ada kondisi khusus."
            _advice.value = when (val r = llm.complete(system, listOf(LlmMessage("user", prompt)), maxTokens = 1_200)) {
                is LlmResult.Ok -> AdviceState.Ok(r.text)
                is LlmResult.Failure -> AdviceState.Failed(r.reason)
            }
        }
    }
}

@Composable
fun FuelScreen(onBack: () -> Unit, onOpenKeys: () -> Unit, vm: FuelViewModel = hiltViewModel()) {
    val fuel by vm.fuel.collectAsStateWithLifecycle()
    val sport by vm.sport.collectAsStateWithLifecycle()
    val duration by vm.durationMin.collectAsStateWithLifecycle()
    val temp by vm.tempC.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val advice by vm.advice.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Minum & asupan energi", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = fuel.enabled, role = Role.Switch, onValueChange = { vm.save(fuel.copy(enabled = it)) })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pengingat saat merekam", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Dibacakan lewat voice over (nyalakan di Suara & peringatan), dihitung dari waktu bergerak",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                    )
                }
                Switch(checked = fuel.enabled, onCheckedChange = null)
            }

            SectionTitle("Interval pengingat")
            Column(Modifier.padding(horizontal = 16.dp)) {
                IntervalSlider("Minum", fuel.drinkEveryMin, 0..60, "Otomatis dari suhu") { vm.save(fuel.copy(drinkEveryMin = it)) }
                IntervalSlider("Makan", fuel.eatEveryMin, 0..90, "Otomatis dari durasi") { vm.save(fuel.copy(eatEveryMin = it)) }
            }

            SectionTitle("Rencana latihan")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(sport == Sport.RIDE, { vm.setSport(Sport.RIDE) }, { Text("Ride") })
                    FilterChip(sport == Sport.RUN, { vm.setSport(Sport.RUN) }, { Text("Run") })
                }
                Text("Durasi ${Format.duration(duration * 60_000L)}", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { vm.setDuration((it / 15).roundToInt() * 15) },
                    valueRange = 30f..480f,
                )
                Text(
                    temp?.let { "Suhu sekarang ${it.roundToInt()}°C dari Open-Meteo" } ?: "Suhu belum diketahui, dianggap 27°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                plan?.let { p ->
                    Column(
                        Modifier.fillMaxWidth().background(Palette.Surface, MaterialTheme.shapes.medium).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            StatPair("ml / jam", p.fluidMlPerHour.toString())
                            StatPair("karbo g / jam", p.carbGramsPerHour.toString())
                            StatPair("minum tiap", "${p.drinkEveryMin} mnt")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            StatPair("total cairan", "${p.totalFluidMl} ml")
                            StatPair("total karbo", "${p.totalCarbGrams} g")
                            StatPair("elektrolit", if (p.electrolytes) "perlu" else "opsional")
                        }
                    }
                }
                Text(
                    "Patokan umum, bukan resep medis. Sesuaikan dengan rasa haus dan keringatmu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }

            SectionTitle("Saran AI")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::askAi, enabled = advice != AdviceState.Loading) { Text("Buat rencana dengan AI") }
                when (val a = advice) {
                    AdviceState.Idle -> Text(
                        "Memakai penyedia AI yang sama dengan AI Coach, dengan angka di atas sebagai dasar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                    )
                    AdviceState.Loading -> CircularProgressIndicator()
                    is AdviceState.Ok -> Text(a.text, style = MaterialTheme.typography.bodyMedium)
                    is AdviceState.Failed -> {
                        Text(a.reason, color = Palette.Error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onOpenKeys) { Text("Atur penyedia AI") }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IntervalSlider(label: String, value: Int, range: IntRange, autoText: String, onChange: (Int) -> Unit) {
    Text(
        "$label: ${if (value == 0) autoText else "tiap $value menit"}",
        style = MaterialTheme.typography.bodyLarge,
    )
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange((it / 5).roundToInt() * 5) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
    )
}
