package id.web.agentvaedah.strideway

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

@HiltAndroidApp
class StridewayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        val channel = NotificationChannel(
            RECORDING_CHANNEL,
            getString(R.string.recording_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.recording_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val RECORDING_CHANNEL = "recording"
    }
}
