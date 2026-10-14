package dev.xplaytime.util

import java.time.Duration

object DurationFormat {
    fun pretty(duration: Duration): String {
        var remaining = duration.seconds.coerceAtLeast(0)
        val days = remaining / 86_400
        remaining %= 86_400
        val hours = remaining / 3_600
        remaining %= 3_600
        val minutes = remaining / 60
        val seconds = remaining % 60

        val parts = ArrayList<String>(4)
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (minutes > 0) parts += "${minutes}m"
        if (seconds > 0 || parts.isEmpty()) parts += "${seconds}s"
        return parts.joinToString(" ")
    }
}
