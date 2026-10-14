package id.web.agentvaedah.strideway.service

import id.web.agentvaedah.strideway.core.training.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The structured session the rider chose to ride next. Set from the Latihan tab, picked up by
 * [RecordingCueRunner] when recording starts, and cleared when the session ends.
 */
@Singleton
class WorkoutSession @Inject constructor() {
    private val _active = MutableStateFlow<Workout?>(null)
    val active: StateFlow<Workout?> = _active.asStateFlow()

    /** Planned-session row this ride fulfils, so the plan can tick it off. */
    var plannedSessionId: Long? = null
        private set

    private val _finished = MutableStateFlow<Pair<Workout, Long>?>(null)

    /** The last session completed and the activity that rode it; consumed by the FTP-test result dialog. */
    val finished: StateFlow<Pair<Workout, Long>?> = _finished.asStateFlow()

    fun select(w: Workout?, plannedSessionId: Long? = null) {
        _active.value = w
        this.plannedSessionId = plannedSessionId
    }

    fun finish(activityId: Long) {
        val w = _active.value ?: return
        _finished.value = w to activityId
        _active.value = null
    }

    fun consumeFinished() {
        _finished.value = null
    }
}
