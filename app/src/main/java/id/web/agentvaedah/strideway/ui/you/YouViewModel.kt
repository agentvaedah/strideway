package id.web.agentvaedah.strideway.ui.you

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.data.ble.BleHub
import id.web.agentvaedah.strideway.data.ble.SensorStatus
import id.web.agentvaedah.strideway.data.db.SensorDao
import id.web.agentvaedah.strideway.data.db.SensorEntity
import id.web.agentvaedah.strideway.data.files.BackupManager
import id.web.agentvaedah.strideway.data.files.TrackFileIo
import id.web.agentvaedah.strideway.data.prefs.PrivacyZoneSetting
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.service.RecordingController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Text-field form state; values stay strings until validation so partial input is allowed. */
data class ProfileForm(
    val gender: Gender,
    val height: String,
    val weight: String,
    val age: String,
    val hrMax: String,
    val hrRest: String,
    val ftp: String,
    val bikeWeight: String,
) {
    fun errors(): Map<String, String> = buildMap {
        fun checkD(key: String, v: String, range: ClosedFloatingPointRange<Double>, msg: String) {
            val d = v.replace(',', '.').toDoubleOrNull()
            if (d == null || d !in range) put(key, msg)
        }
        fun checkI(key: String, v: String, range: IntRange, msg: String) {
            val i = v.toIntOrNull()
            if (i == null || i !in range) put(key, msg)
        }
        checkD("height", height, 100.0..230.0, "100 sampai 230 cm")
        checkD("weight", weight, 30.0..200.0, "30 sampai 200 kg")
        checkI("age", age, 10..90, "10 sampai 90 tahun")
        checkI("hrMax", hrMax, 120..230, "120 sampai 230 bpm")
        checkI("hrRest", hrRest, 30..100, "30 sampai 100 bpm")
        checkI("ftp", ftp, 50..600, "50 sampai 600 W")
        checkD("bikeWeight", bikeWeight, 3.0..40.0, "3 sampai 40 kg")
        val max = hrMax.toIntOrNull()
        val rest = hrRest.toIntOrNull()
        if (max != null && rest != null && rest >= max) put("hrRest", "Harus di bawah HR max")
    }

    fun toAthlete(): Athlete? {
        if (errors().isNotEmpty()) return null
        return Athlete(
            gender = gender,
            heightCm = height.replace(',', '.').toDouble(),
            weightKg = weight.replace(',', '.').toDouble(),
            age = age.toInt(),
            hrMax = hrMax.toInt(),
            hrRest = hrRest.toInt(),
            ftpWatts = ftp.toInt(),
            bikeWeightKg = bikeWeight.replace(',', '.').toDouble(),
        )
    }

    companion object {
        fun from(a: Athlete) = ProfileForm(
            a.gender, trim(a.heightCm), trim(a.weightKg), a.age.toString(), a.hrMax.toString(),
            a.hrRest.toString(), a.ftpWatts.toString(), trim(a.bikeWeightKg),
        )

        private fun trim(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
    }
}

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Working : BackupStatus
    data class Done(val message: String) : BackupStatus
    data class Failed(val message: String) : BackupStatus
}

@HiltViewModel
class YouViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupManager,
    private val files: TrackFileIo,
    sensorDao: SensorDao,
    ble: BleHub,
    private val recording: RecordingController,
) : ViewModel() {
    val athlete: StateFlow<Athlete?> = settings.athlete.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profileSaved: StateFlow<Boolean> = settings.profileSaved.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val privacy: StateFlow<PrivacyZoneSetting> = settings.privacyZone.stateIn(viewModelScope, SharingStarted.Eagerly, PrivacyZoneSetting())
    val sensors: StateFlow<List<SensorEntity>> = sensorDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sensorStatus: StateFlow<Map<String, SensorStatus>> = ble.status

    private val _form = MutableStateFlow<ProfileForm?>(null)
    val form: StateFlow<ProfileForm?> = _form.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _backup = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backup.asStateFlow()

    init {
        viewModelScope.launch { _form.value = ProfileForm.from(settings.athlete.first()) }
    }

    fun edit(transform: (ProfileForm) -> ProfileForm) {
        _form.value = _form.value?.let(transform)
        _saved.value = false
    }

    fun save() {
        val a = _form.value?.toAthlete() ?: return
        viewModelScope.launch {
            settings.saveAthlete(a)
            _saved.value = true
        }
    }

    fun exportBackup(uri: Uri) {
        _backup.value = BackupStatus.Working
        viewModelScope.launch {
            val (json, summary) = backup.export()
            _backup.value = files.write(uri, json).fold(
                onSuccess = { BackupStatus.Done("Backup tersimpan: ${summary.activities} aktivitas, ${summary.routes} rute.") },
                onFailure = { BackupStatus.Failed("Backup gagal: ${it.message}") },
            )
        }
    }

    fun restoreBackup(uri: Uri) {
        // Restore wipes the activity table, which would orphan the live session's track points.
        if (recording.isStartingOrActive) {
            _backup.value = BackupStatus.Failed("Selesaikan rekaman yang sedang berjalan sebelum restore.")
            return
        }
        _backup.value = BackupStatus.Working
        viewModelScope.launch {
            val text = files.readText(uri).getOrElse {
                _backup.value = BackupStatus.Failed("Restore gagal: ${it.message}")
                return@launch
            }
            _backup.value = backup.restore(text).fold(
                onSuccess = {
                    _form.value = ProfileForm.from(settings.athlete.first())
                    BackupStatus.Done("Restore selesai: ${it.activities} aktivitas, ${it.routes} rute.")
                },
                onFailure = { BackupStatus.Failed("Restore gagal: ${it.message}") },
            )
        }
    }

    fun dismissBackupStatus() {
        _backup.value = BackupStatus.Idle
    }
}
