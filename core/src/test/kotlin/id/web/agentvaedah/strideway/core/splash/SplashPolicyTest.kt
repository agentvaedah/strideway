package id.web.agentvaedah.strideway.core.splash

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SplashPolicyTest {
    @Test
    fun `shows on every cold start`() {
        assertTrue(SplashPolicy.shouldShow(restored = false, recordingActive = false, animationsEnabled = true))
    }

    @Test
    fun `skips when the activity is restored after rotation or process death`() {
        assertFalse(SplashPolicy.shouldShow(restored = true, recordingActive = false, animationsEnabled = true))
    }

    @Test
    fun `skips while a ride is being recorded`() {
        assertFalse(SplashPolicy.shouldShow(restored = false, recordingActive = true, animationsEnabled = true))
    }

    @Test
    fun `skips when system animations are turned off`() {
        assertFalse(SplashPolicy.shouldShow(restored = false, recordingActive = false, animationsEnabled = false))
    }
}
