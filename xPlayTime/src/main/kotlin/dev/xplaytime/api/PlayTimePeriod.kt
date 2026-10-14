package dev.xplaytime.api

/**
 * Playback windows used by commands and the public API.
 */
enum class PlayTimePeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    TOTAL;

    companion object {
        fun fromArg(raw: String): PlayTimePeriod? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}
