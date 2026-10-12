package id.web.agentvaedah.strideway

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import id.web.agentvaedah.strideway.service.HardwareKey
import id.web.agentvaedah.strideway.service.HardwareKeyBus
import id.web.agentvaedah.strideway.service.RecordingController
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.navigation.StridewayNavHost
import id.web.agentvaedah.strideway.ui.theme.StridewayTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var keyBus: HardwareKeyBus
    @Inject lateinit var recording: RecordingController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StridewayTheme {
                val state by recording.state.collectAsStateWithLifecycle()
                StridewayNavHost(rememberNavController(), recording = state is RecordingState.Active)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> HardwareKey.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> HardwareKey.VOLUME_DOWN
            else -> null
        }
        if (key != null && keyBus.recordScreenActive) {
            // Consume both down and up so the system volume panel never appears; act on down only.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) keyBus.emit(key)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
