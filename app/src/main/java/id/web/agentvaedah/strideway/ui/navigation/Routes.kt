package id.web.agentvaedah.strideway.ui.navigation

object Routes {
    const val HOME = "home"
    const val MAPS = "maps"
    const val RECORD = "record"
    const val COACH = "coach"
    const val YOU = "you"

    const val ACTIVITY = "activity/{id}"
    fun activity(id: Long) = "activity/$id"

    const val COACH_KEYS = "coach/keys"
    const val DEVICES = "you/devices"
    const val PRIVACY = "you/privacy"
}
