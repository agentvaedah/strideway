package id.web.agentvaedah.strideway.core.splash

object SplashPolicy {
    /**
     * The full splash plays every time the app is opened fresh, after it was closed or its process ended.
     * Returning from the background keeps the existing activity, so it never reaches this check. A restored
     * activity (rotation, process death mid-session) and an active recording go straight to the app, and so
     * does a phone with system animations turned off.
     */
    fun shouldShow(restored: Boolean, recordingActive: Boolean, animationsEnabled: Boolean): Boolean =
        !restored && !recordingActive && animationsEnabled
}
