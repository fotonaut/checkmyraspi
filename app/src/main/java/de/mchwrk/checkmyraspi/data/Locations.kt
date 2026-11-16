package de.mchwrk.checkmyraspi.data

/** Reine Hilfsfunktionen rund um Standorte (ohne Android, daher testbar). */
object Locations {
    const val NONE = "Ohne Standort"

    /** Vereinheitlicht Trenner: „Keller / Rack>oben“ → „Keller › Rack › oben“. */
    fun normalize(raw: String): String = raw
        .split('›', '/', '>', '\\')
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotEmpty() }
        .joinToString(" › ")

    /** Oberster Teil des Standorts, z. B. „Keller“ – danach wird in der Übersicht gruppiert. */
    fun topLevel(location: String): String = normalize(location).substringBefore(" › ").ifEmpty { NONE }

    /** Vorschläge: alle bisher verwendeten Standorte und ihre Oberbegriffe, die zum Eingetippten passen. */
    fun suggestions(devices: List<Device>, typed: String, limit: Int = 6): List<String> {
        val all = devices.map { normalize(it.location) }.filter { it.isNotEmpty() }
        val withParents = all.flatMap { loc ->
            val parts = loc.split(" › ")
            parts.indices.map { parts.take(it + 1).joinToString(" › ") }
        }
        val counts = withParents.groupingBy { it }.eachCount()
        val q = typed.trim()
        return counts.keys
            .filter { q.isEmpty() || it.contains(q, ignoreCase = true) }
            .filter { !it.equals(normalize(q), ignoreCase = true) }
            .sortedWith(compareByDescending<String> { counts[it] }.thenBy { it.lowercase() })
            .take(limit)
    }

    /** Gruppiert nach oberstem Standort; „Ohne Standort“ kommt ans Ende. */
    fun group(devices: List<Device>): List<Pair<String, List<Device>>> = devices
        .groupBy { topLevel(it.location) }
        .toList()
        .sortedWith(compareBy<Pair<String, List<Device>>> { it.first == NONE }.thenBy { it.first.lowercase() })
        .map { (k, v) -> k to v.sortedBy { it.name.lowercase() } }

    /** Suche über Name, Host, Standort, Notiz, Beschreibung, Modell, Seriennummer und MAC. */
    fun matches(device: Device, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOfNotNull(
            device.name, device.host, device.location, device.locationNote, device.description,
            device.model, device.serial, device.mac,
        ).any { it.contains(q, ignoreCase = true) }
    }
}
