package id.web.agentvaedah.strideway.core.splash

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SplashPolicyTest {
    @Test
    fun `shows on the first cold start after install`() {
        assertTrue(SplashPolicy.shouldShow(seen = false, restored = false, recordingActive = false, animationsEnabled = true))
    }

    @Test
    fun `skips once it has been seen`() {
        assertFalse(SplashPolicy.shouldShow(seen = true, restored = false, recordingActive = false, animationsEnabled = true))
    }

    @Test
    fun `skips when the activity is restored after rotation or process death`() {
        assertFalse(SplashPolicy.shouldShow(seen = false, restored = true, recordingActive = false, animationsEnabled = true))
    }

    @Test
    fun `skips while a ride is being recorded`() {
        assertFalse(SplashPolicy.shouldShow(seen = false, restored = false, recordingActive = true, animationsEnabled = true))
    }

    @Test
    fun `skips when system animations are turned off`() {
        assertFalse(SplashPolicy.shouldShow(seen = false, restored = false, recordingActive = false, animationsEnabled = false))
    }
}
