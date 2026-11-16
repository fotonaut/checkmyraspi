package de.mchwrk.checkmyraspi.ui

import java.util.Locale

object Format {
    fun uptime(seconds: Long): String {
        val d = seconds / 86_400
        val h = (seconds % 86_400) / 3_600
        val m = (seconds % 3_600) / 60
        return when {
            d > 0 -> "$d T $h h"
            h > 0 -> "$h h $m min"
            else -> "$m min"
        }
    }

    /** Kilobyte → „3,7 GB“. */
    fun kb(kb: Long): String {
        val units = listOf("KB", "MB", "GB", "TB")
        var v = kb.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (v >= 100 || i == 0) String.format(Locale.GERMANY, "%.0f %s", v, units[i])
        else String.format(Locale.GERMANY, "%.1f %s", v, units[i])
    }

    fun ago(thenMs: Long, nowMs: Long): String {
        if (thenMs <= 0) return "nie"
        val s = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "gerade eben"
            s < 3_600 -> "vor ${s / 60} min"
            s < 86_400 -> "vor ${s / 3_600} h"
            else -> "vor ${s / 86_400} T"
        }
    }

    fun temp(c: Double) = String.format(Locale.GERMANY, "%.1f °C", c)
    fun mhz(hz: Long) = "${hz / 1_000_000} MHz"
    fun volts(v: Double) = String.format(Locale.GERMANY, "%.2f V", v)
    fun load(v: Double) = String.format(Locale.GERMANY, "%.2f", v)
}
