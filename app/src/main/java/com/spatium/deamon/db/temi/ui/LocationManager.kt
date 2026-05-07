package com.spatium.deamon.db.temi.ui

import android.util.Log

class LocationManager(
    private val onLocationsReady: (List<String>) -> Unit,
    private val onLocationsEmpty: () -> Unit,
) {

    companion object {
        private const val TAG = "LocationManager"
        const val EVENT_PREFIX = "spatiumfoto"
    }

    private var eventLocations: MutableList<String> = mutableListOf()

    fun loadLocations(robot: com.robotemi.sdk.Robot) {
        loadLocationsWithPrefix(robot, EVENT_PREFIX)
    }

    fun loadLocationsWithPrefix(robot: com.robotemi.sdk.Robot, prefix: String) {
        try {
            val all = robot.locations
            Log.d(TAG, "[LOCATIONS] Total puntos en mapa: ${all.size} → $all")

            eventLocations = all
                .filter { it.startsWith(prefix, ignoreCase = true) }
                .sorted()
                .toMutableList()

            Log.d(TAG, "[LOCATIONS] Puntos de evento (prefijo '$prefix'): $eventLocations")

            if (eventLocations.isEmpty()) {
                Log.w(TAG, "[LOCATIONS] ⚠️ No hay puntos con prefijo '$prefix'")
                onLocationsEmpty()
            } else {
                Log.d(TAG, "[LOCATIONS] ✓ ${eventLocations.size} puntos listos")
                onLocationsReady(eventLocations.toList())
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[LOCATIONS] Error cargando ubicaciones: ${t.message}", t)
            onLocationsEmpty()
        }
    }

    fun getNextRandom(lastLocation: String?): String? {
        if (eventLocations.isEmpty()) return null
        if (eventLocations.size == 1) return eventLocations.first()
        val candidates = eventLocations.filter { it != lastLocation }
        return candidates.random()
    }

    fun isEmpty() = eventLocations.isEmpty()

    fun extractEventPrefixes(locations: List<String>): Map<String, List<String>> = locations
        .filter { it.contains("_") }
        .groupBy { loc ->
            val parts = loc.split("_")
            parts.dropLast(1).joinToString("_")
        }
        .filter { (_, points) -> points.size >= 2 }
}
