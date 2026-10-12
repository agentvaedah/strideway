package id.web.agentvaedah.strideway.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import id.web.agentvaedah.strideway.MainActivity
import id.web.agentvaedah.strideway.R
import id.web.agentvaedah.strideway.StridewayApp
import id.web.agentvaedah.strideway.core.format.Format
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps the process and CPU alive while recording; all logic lives in [RecordingController]. */
@AndroidEntryPoint
class RecordingService : LifecycleService() {
    @Inject lateinit var controller: RecordingController

    private var wakeLock: PowerManager.WakeLock? = null
    private var lastText: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build("Menunggu sinyal GPS"), type)

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Strideway:recording")
                .apply { acquire(MAX_WAKE_MS) }
        }

        lifecycleScope.launch {
            controller.state.collectLatest { s ->
                if (s !is RecordingState.Active) return@collectLatest
                val m = s.metrics
                val text = buildString {
                    append(Format.duration(m.movingMs)).append("  ·  ")
                    append(Format.km(m.distanceM)).append(" km")
                    if (m.paused) append("  ·  Jeda")
                }
                if (text != lastText) {
                    lastText = text
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(text))
                }
            }
        }
        return START_STICKY
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, StridewayApp.RECORDING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle("Strideway merekam")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 42
        const val MAX_WAKE_MS = 24L * 60 * 60 * 1000
    }
}
