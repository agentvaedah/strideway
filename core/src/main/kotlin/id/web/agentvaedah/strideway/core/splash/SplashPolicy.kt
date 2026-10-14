package id.web.agentvaedah.strideway.core.splash

object SplashPolicy {
    /**
     * The full splash plays once, on the first launch after install; later launches show only the system
     * dot. A restored activity (rotation, process death) and an active recording go straight to the app,
     * and so does a phone with system animations turned off.
     */
    fun shouldShow(seen: Boolean, restored: Boolean, recordingActive: Boolean, animationsEnabled: Boolean): Boolean =
        !seen && !restored && !recordingActive && animationsEnabled
}
