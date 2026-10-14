package id.web.agentvaedah.strideway.ui.navigation

object Routes {
    const val HOME = "home"
    const val MAPS = "maps"
    const val RECORD = "record"
    const val TRAINING = "training"
    const val COACH = "training/coach"
    const val YOU = "you"

    const val ACTIVITY = "activity/{id}"
    fun activity(id: Long) = "activity/$id"

    const val COACH_KEYS = "coach/keys"
    const val DEVICES = "you/devices"
    const val PRIVACY = "you/privacy"
    const val VOICE = "you/voice"
    const val SETTINGS = "you/settings"
    const val PROFILE = "you/profile"
    const val MAP_SETTINGS = "you/maps"
    const val FUEL = "you/fuel"
    const val WEATHER = "weather"
    const val GARAGE = "you/garage"
}
