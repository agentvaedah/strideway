package id.web.agentvaedah.strideway.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class HardwareKey { VOLUME_UP, VOLUME_DOWN }

/**
 * Volume keys act as head-unit buttons only while the Record screen is visible;
 * everywhere else they keep adjusting media volume.
 */
@Singleton
class HardwareKeyBus @Inject constructor() {
    @Volatile
    var recordScreenActive: Boolean = false

    private val _events = MutableSharedFlow<HardwareKey>(extraBufferCapacity = 8)
    val events: SharedFlow<HardwareKey> = _events.asSharedFlow()

    /** Returns true when the key was consumed. */
    fun emit(key: HardwareKey): Boolean {
        if (!recordScreenActive) return false
        return _events.tryEmit(key)
    }
}
