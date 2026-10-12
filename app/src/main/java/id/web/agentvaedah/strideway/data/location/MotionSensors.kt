package id.web.agentvaedah.strideway.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MotionSample {
    /** Horizontal linear acceleration in the world frame (m/s², east and north components). */
    data class Acceleration(val east: Double, val north: Double, val timeMs: Long) : MotionSample

    /** Device azimuth, 0 = north, clockwise. */
    data class Heading(val degrees: Float, val timeMs: Long) : MotionSample

    /** Barometric altitude relative to standard atmosphere; needs a GPS offset to be absolute. */
    data class Pressure(val altitudeM: Double, val timeMs: Long) : MotionSample
}

@Singleton
class MotionSensors @Inject constructor(@ApplicationContext context: Context) {
    private val manager = context.getSystemService(SensorManager::class.java)

    val hasBarometer: Boolean get() = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    fun samples(): Flow<MotionSample> = callbackFlow {
        val sm = manager
        if (sm == null) {
            close()
            return@callbackFlow
        }
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var haveRotation = false
        var lastHeadingMs = 0L
        var lastPressureMs = 0L
        var smoothedAltitude: Double? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val now = System.currentTimeMillis()
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotation, e.values)
                        haveRotation = true
                        if (now - lastHeadingMs >= HEADING_PERIOD_MS) {
                            lastHeadingMs = now
                            SensorManager.getOrientation(rotation, orientation)
                            val deg = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                            trySend(MotionSample.Heading(deg, now))
                        }
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION -> if (haveRotation) {
                        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                        val east = rotation[0] * x + rotation[1] * y + rotation[2] * z
                        val north = rotation[3] * x + rotation[4] * y + rotation[5] * z
                        trySend(MotionSample.Acceleration(east.toDouble(), north.toDouble(), now))
                    }
                    Sensor.TYPE_PRESSURE -> {
                        val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, e.values[0]).toDouble()
                        smoothedAltitude = smoothedAltitude?.let { it + (alt - it) * 0.2 } ?: alt
                        if (now - lastPressureMs >= PRESSURE_PERIOD_MS) {
                            lastPressureMs = now
                            trySend(MotionSample.Pressure(smoothedAltitude!!, now))
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        listOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_LINEAR_ACCELERATION).forEach { type ->
            sm.getDefaultSensor(type)?.let { sm.registerListener(listener, it, IMU_PERIOD_US) }
        }
        sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        awaitClose { sm.unregisterListener(listener) }
    }

    private companion object {
        const val IMU_PERIOD_US = 20_000 // 50 Hz (PRD 3.1)
        const val HEADING_PERIOD_MS = 200L
        const val PRESSURE_PERIOD_MS = 1_000L
    }
}
