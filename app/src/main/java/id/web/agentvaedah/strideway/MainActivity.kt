package id.web.agentvaedah.strideway

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import id.web.agentvaedah.strideway.core.splash.SplashPolicy
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.prefs.ThemeMode
import id.web.agentvaedah.strideway.service.HardwareKey
import id.web.agentvaedah.strideway.service.HardwareKeyBus
import id.web.agentvaedah.strideway.service.RecordingController
import id.web.agentvaedah.strideway.service.RecordingState
import id.web.agentvaedah.strideway.ui.components.LocalMapboxToken
import id.web.agentvaedah.strideway.ui.navigation.StridewayNavHost
import id.web.agentvaedah.strideway.ui.splash.StridewaySplash
import id.web.agentvaedah.strideway.ui.theme.StridewayTheme
import id.web.agentvaedah.strideway.ui.theme.isDarkTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var keyBus: HardwareKeyBus
    @Inject lateinit var recording: RecordingController
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemSplash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // StridewaySplash draws the same dot in the same place on its first frame, so no exit animation.
        systemSplash.setOnExitAnimationListener { it.remove() }
        val showSplash = SplashPolicy.shouldShow(
            restored = savedInstanceState != null,
            recordingActive = recording.state.value is RecordingState.Active,
            animationsEnabled = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f,
        )
        enableEdgeToEdge()
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dark = isDarkTheme(themeMode)
            // Status and navigation bar icons follow the in-app theme, not only the system setting.
            LaunchedEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            val mapboxToken by settings.mapboxToken.collectAsStateWithLifecycle(null)
            StridewayTheme(themeMode) {
                CompositionLocalProvider(LocalMapboxToken provides mapboxToken) {
                    val state by recording.state.collectAsStateWithLifecycle()
                    var splashVisible by remember { mutableStateOf(showSplash) }
                    // An interrupted ride can be recovered while the splash plays; get out of its way.
                    LaunchedEffect(state) { if (state is RecordingState.Active) splashVisible = false }
                    // Home composes under the splash, so its data is ready when the splash fades.
                    Box(Modifier.fillMaxSize()) {
                        StridewayNavHost(rememberNavController(), recording = state is RecordingState.Active)
                        AnimatedVisibility(visible = splashVisible, enter = EnterTransition.None, exit = fadeOut(tween(250))) {
                            StridewaySplash(onFinished = { splashVisible = false })
                        }
                    }
                }
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
