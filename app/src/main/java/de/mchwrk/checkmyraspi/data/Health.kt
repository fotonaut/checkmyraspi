package de.mchwrk.checkmyraspi.data

import de.mchwrk.checkmyraspi.ssh.RaspiStatus

enum class Severity { INFO, WARN, CRITICAL }

data class Issue(val severity: Severity, val text: String)

/** Leitet aus einem Status verständliche Hinweise ab – für Badges in der Übersicht. */
object Health {
    const val TEMP_WARN = 70.0
    const val TEMP_CRIT = 80.0
    const val FULL_WARN = 90

    fun issues(s: RaspiStatus): List<Issue> = buildList {
        val t = s.throttle
        if (t != null) {
            if (t.underVoltageNow) add(Issue(Severity.CRITICAL, "Unterspannung"))
            else if (t.underVoltageOccurred) add(Issue(Severity.WARN, "Unterspannung seit Boot"))
            if (t.throttledNow) add(Issue(Severity.CRITICAL, "Gedrosselt"))
            else if (t.softTempLimitNow || t.freqCappedNow) add(Issue(Severity.WARN, "Takt begrenzt"))
        }
        s.cpuTempC?.let {
            when {
                it >= TEMP_CRIT -> add(Issue(Severity.CRITICAL, "%.0f °C".format(it)))
                it >= TEMP_WARN -> add(Issue(Severity.WARN, "%.0f °C".format(it)))
                else -> Unit
            }
        }
        s.disks.filter { it.usedPercent >= FULL_WARN }.forEach {
            add(Issue(Severity.WARN, "${it.mount} ${it.usedPercent} % voll"))
        }
        s.memUsedPercent?.takeIf { it >= FULL_WARN }?.let { add(Issue(Severity.WARN, "RAM $it %")) }
        if (s.rebootRequired) add(Issue(Severity.INFO, "Neustart nötig"))
        s.upgradable?.takeIf { it > 0 }?.let { add(Issue(Severity.INFO, "$it Updates")) }
    }

    fun worst(issues: List<Issue>): Severity? = issues.maxByOrNull { it.severity.ordinal }?.severity
}
