package id.web.agentvaedah.strideway.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.weather.DailyWeather
import id.web.agentvaedah.strideway.core.weather.HourlyWeather
import id.web.agentvaedah.strideway.core.weather.WeatherCodes
import id.web.agentvaedah.strideway.core.weather.WeatherKind
import id.web.agentvaedah.strideway.core.weather.WeatherReport
import id.web.agentvaedah.strideway.data.location.GpsSource
import id.web.agentvaedah.strideway.data.location.MotionSensors
import id.web.agentvaedah.strideway.data.remote.weather.WeatherRepository
import id.web.agentvaedah.strideway.data.remote.weather.WeatherState
import id.web.agentvaedah.strideway.ui.components.BackTopBar
import id.web.agentvaedah.strideway.ui.components.BentoCell
import id.web.agentvaedah.strideway.ui.components.BentoGrid
import id.web.agentvaedah.strideway.ui.components.BentoStat
import id.web.agentvaedah.strideway.ui.components.DefaultCenter
import id.web.agentvaedah.strideway.ui.components.ErrorState
import id.web.agentvaedah.strideway.ui.components.LoadingState
import id.web.agentvaedah.strideway.ui.components.SectionTitle
import id.web.agentvaedah.strideway.ui.theme.MetricText
import id.web.agentvaedah.strideway.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val idLocale = Locale.forLanguageTag("id-ID")
private val hourFmt = DateTimeFormatter.ofPattern("HH:mm", idLocale)
private val dayFmt = DateTimeFormatter.ofPattern("EEE d", idLocale)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weather: WeatherRepository,
    private val gps: GpsSource,
    motion: MotionSensors,
) : ViewModel() {
    val state: StateFlow<WeatherState> = weather.state
    val hasBarometer = motion.hasBarometer

    init { refresh(false) }

    fun refresh(force: Boolean = true) {
        val p = gps.lastKnown() ?: DefaultCenter
        viewModelScope.launch { weather.refresh(p.lat, p.lon, force) }
    }
}

fun WeatherKind.icon(isDay: Boolean = true): ImageVector = when (this) {
    WeatherKind.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode
    WeatherKind.PARTLY_CLOUDY -> Icons.Outlined.FilterDrama
    WeatherKind.CLOUDY, WeatherKind.FOG -> Icons.Outlined.Cloud
    WeatherKind.DRIZZLE -> Icons.Outlined.Grain
    WeatherKind.RAIN -> Icons.Outlined.WaterDrop
    WeatherKind.THUNDERSTORM -> Icons.Outlined.Thunderstorm
    WeatherKind.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
}

@Composable
fun WeatherScreen(onBack: () -> Unit, vm: WeatherViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Cuaca", onBack) {
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Outlined.Refresh, contentDescription = "Muat ulang cuaca") }
        }
        when (val s = state) {
            WeatherState.Idle, WeatherState.Loading -> LoadingState("Memuat cuaca", Modifier.fillMaxSize())
            is WeatherState.Failed -> ErrorState(s.message, Modifier.fillMaxSize(), onRetry = { vm.refresh() })
            is WeatherState.Ready -> WeatherContent(s.report, vm.hasBarometer)
        }
    }
}

@Composable
private fun WeatherContent(r: WeatherReport, hasBarometer: Boolean) {
    val c = r.current
    val today = r.daily.firstOrNull()
    val nextRain = r.hourly.take(6).maxOfOrNull { it.rainProbPct } ?: 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(c.kind.icon(c.isDay), contentDescription = null, modifier = Modifier.size(56.dp), tint = Palette.Accent)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("${c.tempC.roundToInt()}°", style = MetricText.Huge)
                Text("${c.kind.label} · terasa ${c.feelsLikeC.roundToInt()}°", style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted)
                today?.let {
                    Text("Tertinggi ${it.maxC.roundToInt()}° · terendah ${it.minC.roundToInt()}°", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Palette.Surface, MaterialTheme.shapes.medium).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Untuk latihan", style = MaterialTheme.typography.titleSmall)
            WeatherCodes.rideAdvice(c.tempC, nextRain, c.uvIndex, c.windKmh, c.kind).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionTitle("Sekarang")
        // Wind matters most on a bike, so it spans; the rest pair up by what they help decide.
        BentoGrid(
            buildList {
                add(
                    BentoCell(span = 2) {
                        BentoStat(
                            "Angin",
                            c.windKmh.roundToInt().toString(),
                            "km/j",
                            caption = WeatherCodes.windFrom(c.windDirDeg) + (c.windGustKmh?.let { ", hembusan ${it.roundToInt()} km/j" } ?: ""),
                        )
                    },
                )
                add(BentoCell { BentoStat("Hujan 6 jam", "$nextRain", "%", valueStyle = MetricText.Small) })
                add(BentoCell { BentoStat("Indeks UV", c.uvIndex?.let { "%.1f".format(Locale.US, it).replace('.', ',') } ?: "--", valueStyle = MetricText.Small) })
                add(BentoCell { BentoStat("Kelembapan", "${c.humidityPct}", "%", valueStyle = MetricText.Small) })
                add(BentoCell { BentoStat("Tekanan", c.pressureHpa?.roundToInt()?.toString() ?: "--", "hPa", valueStyle = MetricText.Small) })
                if (today?.sunriseMs != null || today?.sunsetMs != null) {
                    add(
                        BentoCell(span = 2) {
                            BentoStat(
                                "Matahari",
                                "${today?.sunriseMs?.let(::time) ?: "--"} - ${today?.sunsetMs?.let(::time) ?: "--"}",
                                caption = "Terbit dan terbenam",
                                valueStyle = MetricText.Small,
                            )
                        },
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        SectionTitle("24 jam ke depan")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(r.hourly, key = { it.timeMs }) { HourCell(it) }
        }

        SectionTitle("7 hari")
        r.daily.forEach { DayRow(it) }

        Text(
            "Data cuaca: Open-Meteo (CC BY 4.0). " +
                if (hasBarometer) "Ponsel ini punya barometer; dipakai untuk elevasi saat merekam." else "Ponsel ini tanpa barometer; elevasi saat merekam memakai GPS.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextMuted,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun HourCell(h: HourlyWeather) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time(h.timeMs), style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        Icon(h.kind.icon(), contentDescription = h.kind.label, tint = Palette.Text, modifier = Modifier.padding(vertical = 4.dp))
        Text("${h.tempC.roundToInt()}°", style = MaterialTheme.typography.titleSmall)
        Text("${h.rainProbPct}%", style = MaterialTheme.typography.labelMedium, color = Palette.Info)
    }
}

@Composable
private fun DayRow(d: DailyWeather) {
    val date = LocalDate.ofEpochDay(d.dateEpochDay)
    val label = if (date == LocalDate.now()) "Hari ini" else date.format(dayFmt).replaceFirstChar { it.titlecase(idLocale) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(88.dp))
        Icon(d.kind.icon(), contentDescription = d.kind.label, tint = Palette.Text)
        Spacer(Modifier.width(8.dp))
        Text("${d.rainProbPct}%", style = MaterialTheme.typography.bodyMedium, color = Palette.Info, modifier = Modifier.width(48.dp))
        Text(d.kind.label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(1f))
        Text("${d.minC.roundToInt()}° / ${d.maxC.roundToInt()}°", style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(horizontal = 16.dp))
}

private fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hourFmt)
