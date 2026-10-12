package id.web.agentvaedah.strideway.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.core.model.LatLon
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GpsSample(
    val timeMs: Long,
    val pos: LatLon,
    val altitudeM: Double?,
    val speedMps: Double?,
    val bearingDeg: Float?,
    val accuracyM: Float?,
)

/**
 * Raw GNSS through LocationManager.GPS_PROVIDER at 500 ms / 0 m (PRD 3.1). Fused location is
 * avoided on purpose: it batches fixes and smooths corners, which lags a head unit.
 */
@Singleton
class GpsSource @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(): Boolean = manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    @SuppressLint("MissingPermission")
    fun lastKnown(): LatLon? {
        if (!hasPermission()) return null
        val loc = manager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: manager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return loc?.let { LatLon(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    fun updates(): Flow<GpsSample> = callbackFlow {
        val lm = manager
        if (lm == null || !hasPermission()) {
            close(IllegalStateException("Izin lokasi belum diberikan"))
            return@callbackFlow
        }
        // A full object, not a lambda: on Android 10 and older the provider callbacks are
        // abstract, and a SAM lambda crashes with AbstractMethodError when GPS is toggled.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toSample())
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper())
        awaitClose { lm.removeUpdates(listener) }
    }

    private fun Location.toSample() = GpsSample(
        timeMs = System.currentTimeMillis(),
        pos = LatLon(latitude, longitude),
        altitudeM = if (hasAltitude()) altitude else null,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        bearingDeg = if (hasBearing()) bearing else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
    )

    private companion object {
        const val INTERVAL_MS = 500L
    }
}
