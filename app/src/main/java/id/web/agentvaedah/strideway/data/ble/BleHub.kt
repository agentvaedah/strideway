package id.web.agentvaedah.strideway.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.core.sensor.CadenceCalculator
import id.web.agentvaedah.strideway.core.sensor.GattParsers
import id.web.agentvaedah.strideway.core.sensor.WheelSpeedCalculator
import id.web.agentvaedah.strideway.data.db.SensorDao
import id.web.agentvaedah.strideway.data.db.SensorEntity
import id.web.agentvaedah.strideway.data.db.SensorKind
import id.web.agentvaedah.strideway.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SensorStatus(
    val address: String,
    val name: String,
    val kind: SensorKind,
    val connected: Boolean,
    val connecting: Boolean,
    val batteryPct: Int? = null,
)

sealed interface SensorReading {
    val timeMs: Long

    data class HeartRate(val bpm: Int, override val timeMs: Long) : SensorReading
    data class Cadence(val rpm: Int, override val timeMs: Long) : SensorReading
    data class WheelSpeed(val mps: Double, override val timeMs: Long) : SensorReading
    data class Power(val watts: Int, override val timeMs: Long) : SensorReading
    data class Temperature(val celsius: Double, override val timeMs: Long) : SensorReading
    data class Pressure(val hpa: Double, override val timeMs: Long) : SensorReading
    data class Humidity(val percent: Double, override val timeMs: Long) : SensorReading
}

data class ScannedDevice(val address: String, val name: String, val kind: SensorKind, val rssi: Int)

private fun uuid16(short: Int): UUID = UUID.fromString(String.format(Locale.ROOT, "0000%04x-0000-1000-8000-00805f9b34fb", short))

private object Gatt {
    val HR_SERVICE = uuid16(0x180D)
    val HR_MEASUREMENT = uuid16(0x2A37)
    val CSC_SERVICE = uuid16(0x1816)
    val CSC_MEASUREMENT = uuid16(0x2A5B)
    val POWER_SERVICE = uuid16(0x1818)
    val POWER_MEASUREMENT = uuid16(0x2A63)
    val ENV_SERVICE = uuid16(0x181A)
    val ENV_TEMPERATURE = uuid16(0x2A6E)
    val ENV_PRESSURE = uuid16(0x2A6D)
    val ENV_HUMIDITY = uuid16(0x2A6F)
    val BATTERY_SERVICE = uuid16(0x180F)
    val BATTERY_LEVEL = uuid16(0x2A19)
    val CCCD = uuid16(0x2902)
}

/**
 * Standard BLE fitness sensors: heart rate (0x180D), speed/cadence (0x1816), cycling power (0x1818) and
 * Environmental Sensing (0x181A) for temperature, pressure and humidity.
 * Watches and smartbands show up here only with their "HR broadcast" mode switched on.
 */
@Singleton
class BleHub @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sensorDao: SensorDao,
    @AppScope private val scope: CoroutineScope,
) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _status = MutableStateFlow<Map<String, SensorStatus>>(emptyMap())
    val status: StateFlow<Map<String, SensorStatus>> = _status.asStateFlow()

    private val _readings = MutableSharedFlow<SensorReading>(extraBufferCapacity = 64)
    val readings: SharedFlow<SensorReading> = _readings.asSharedFlow()

    private val _scanResults = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scanResults: StateFlow<List<ScannedDevice>> = _scanResults.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val connections = ConcurrentHashMap<String, Connection>()
    private var scanJob: Job? = null

    @Volatile
    var wheelCircumferenceM: Double = 2.105
        set(value) {
            field = value
            connections.values.forEach { it.wheel.circumferenceM = value }
        }

    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val uuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
            val kind = when {
                Gatt.HR_SERVICE in uuids -> SensorKind.HR
                Gatt.POWER_SERVICE in uuids -> SensorKind.POWER
                Gatt.CSC_SERVICE in uuids -> SensorKind.CSC
                Gatt.ENV_SERVICE in uuids -> SensorKind.ENV
                else -> return
            }
            val name = result.scanRecord?.deviceName ?: result.device.name ?: "Sensor ${result.device.address.takeLast(5)}"
            val device = ScannedDevice(result.device.address, name, kind, result.rssi)
            _scanResults.update { list -> (list.filterNot { it.address == device.address } + device).sortedByDescending { it.rssi } }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(durationMs: Long = 15_000) {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (!hasPermissions() || _scanning.value) return
        _scanResults.value = emptyList()
        val filters = listOf(Gatt.HR_SERVICE, Gatt.CSC_SERVICE, Gatt.POWER_SERVICE, Gatt.ENV_SERVICE).map {
            ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, scanCallback)
        _scanning.value = true
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(durationMs)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        if (_scanning.value && hasPermissions()) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _scanning.value = false
    }

    suspend fun pair(device: ScannedDevice) {
        sensorDao.upsert(SensorEntity(device.address, device.name, device.kind))
        connect(device.address, device.name, device.kind)
    }

    suspend fun forget(address: String) {
        disconnect(address)
        sensorDao.delete(address)
    }

    /** Connects every saved sensor marked auto-connect; called when a recording starts. */
    suspend fun connectSaved() {
        sensorDao.all().filter { it.autoConnect }.forEach { connect(it.address, it.name, it.kind) }
    }

    fun disconnectAll() {
        connections.keys.toList().forEach(::disconnect)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String, kind: SensorKind) {
        if (!hasPermissions() || connections.containsKey(address)) return
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        val conn = Connection(address, name, kind).also { it.wheel.circumferenceM = wheelCircumferenceM }
        connections[address] = conn
        setStatus(address) { SensorStatus(address, name, kind, connected = false, connecting = true) }
        conn.open(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        val conn = connections.remove(address) ?: return
        conn.closed = true
        conn.reconnectJob?.cancel()
        if (hasPermissions()) {
            conn.gatt?.disconnect()
            conn.gatt?.close()
        }
        _status.update { it - address }
    }

    private fun setStatus(address: String, transform: (SensorStatus?) -> SensorStatus) {
        _status.update { it + (address to transform(it[address])) }
    }

    private inner class Connection(val address: String, val name: String, val kind: SensorKind) {
        var gatt: BluetoothGatt? = null
        var closed = false
        var reconnectJob: Job? = null
        var attempt = 0
        val cadence = CadenceCalculator()
        val wheel = WheelSpeedCalculator()
        private val pending = ArrayDeque<(BluetoothGatt) -> Boolean>()

        @SuppressLint("MissingPermission")
        fun open(device: BluetoothDevice) {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }

        private fun scheduleReconnect() {
            if (closed) return
            val wait = BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)]
            attempt++
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(wait)
                if (closed) return@launch
                setStatus(address) { it?.copy(connecting = true) ?: SensorStatus(address, name, kind, false, true) }
                val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return@launch
                open(device)
            }
        }

        /** GATT allows one outstanding operation; the next starts from the previous one's callback. */
        private fun runNext(g: BluetoothGatt) {
            while (pending.isNotEmpty()) {
                if (pending.removeFirst().invoke(g)) return
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
            if (!g.setCharacteristicNotification(ch, true)) return false
            val cccd = ch.getDescriptor(Gatt.CCCD) ?: return false
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        private fun handle(uuid: UUID, value: ByteArray) {
            val now = System.currentTimeMillis()
            when (uuid) {
                Gatt.HR_MEASUREMENT -> GattParsers.heartRate(value)?.let { _readings.tryEmit(SensorReading.HeartRate(it, now)) }
                Gatt.POWER_MEASUREMENT -> GattParsers.cyclingPowerWatts(value)?.let {
                    _readings.tryEmit(SensorReading.Power(it.coerceAtLeast(0), now))
                }
                Gatt.CSC_MEASUREMENT -> {
                    val csc = GattParsers.csc(value)
                    if (csc.crankRevs != null && csc.crankEventTime != null) {
                        cadence.update(csc.crankRevs!!, csc.crankEventTime!!, now)?.let { _readings.tryEmit(SensorReading.Cadence(it, now)) }
                    }
                    if (csc.wheelRevs != null && csc.wheelEventTime != null) {
                        wheel.update(csc.wheelRevs!!, csc.wheelEventTime!!, now)?.let { _readings.tryEmit(SensorReading.WheelSpeed(it, now)) }
                    }
                }
                Gatt.ENV_TEMPERATURE -> GattParsers.temperatureC(value)?.let { _readings.tryEmit(SensorReading.Temperature(it, now)) }
                Gatt.ENV_PRESSURE -> GattParsers.pressureHpa(value)?.let { _readings.tryEmit(SensorReading.Pressure(it, now)) }
                Gatt.ENV_HUMIDITY -> GattParsers.humidityPct(value)?.let { _readings.tryEmit(SensorReading.Humidity(it, now)) }
                Gatt.BATTERY_LEVEL -> GattParsers.battery(value)?.let { pct -> setStatus(address) { it?.copy(batteryPct = pct) ?: SensorStatus(address, name, kind, connected = true, connecting = false, batteryPct = pct) } }
            }
        }

        val callback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    attempt = 0
                    setStatus(address) { SensorStatus(address, name, kind, connected = true, connecting = false, batteryPct = it?.batteryPct) }
                    g.discoverServices()
                } else {
                    g.close()
                    if (gatt === g) gatt = null
                    if (closed) return
                    setStatus(address) { SensorStatus(address, name, kind, connected = false, connecting = false, batteryPct = it?.batteryPct) }
                    scheduleReconnect()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                pending.clear()
                listOf(
                    Gatt.HR_SERVICE to Gatt.HR_MEASUREMENT,
                    Gatt.CSC_SERVICE to Gatt.CSC_MEASUREMENT,
                    Gatt.POWER_SERVICE to Gatt.POWER_MEASUREMENT,
                    Gatt.ENV_SERVICE to Gatt.ENV_TEMPERATURE,
                    Gatt.ENV_SERVICE to Gatt.ENV_PRESSURE,
                    Gatt.ENV_SERVICE to Gatt.ENV_HUMIDITY,
                ).forEach { (service, char) ->
                    g.getService(service)?.getCharacteristic(char)?.let { ch -> pending.addLast { enableNotify(it, ch) } }
                }
                g.getService(Gatt.BATTERY_SERVICE)?.getCharacteristic(Gatt.BATTERY_LEVEL)?.let { ch ->
                    pending.addLast { it.readCharacteristic(ch) }
                }
                runNext(g)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = runNext(g)

            override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) handle(ch.uuid, value)
                runNext(g)
            }

            @Deprecated("Used below API 33")
            override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
                @Suppress("DEPRECATION")
                if (status == BluetoothGatt.GATT_SUCCESS) ch.value?.let { handle(ch.uuid, it) }
                runNext(g)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
                handle(ch.uuid, value)
            }

            @Deprecated("Used below API 33")
            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                ch.value?.let { handle(ch.uuid, it) }
            }
        }
    }

    private companion object {
        val BACKOFF_MS = longArrayOf(2_000, 4_000, 8_000, 16_000, 30_000)
    }
}
