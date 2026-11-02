package com.sinema.ui

internal object MarkerSkipState {
    private const val ENTRY_WINDOW_MS = 1_500L
    private const val MIN_NEXT_DISTANCE_MS = 250L

    fun entered(
        previousMs: Long,
        currentMs: Long,
        markers: List<Pair<String, Double>>
    ): Pair<String, Double>? {
        if (currentMs <= previousMs) return null
        return markers
            .map { it.first to (it.second * 1_000).toLong() }
            .filter { (_, startMs) ->
                startMs > previousMs && startMs <= currentMs && currentMs - startMs <= ENTRY_WINDOW_MS
            }
            .maxByOrNull { it.second }
            ?.let { (title, startMs) -> title to startMs / 1_000.0 }
    }

    fun nextAfter(currentMs: Long, markers: List<Pair<String, Double>>): Long? =
        markers.map { (it.second * 1_000).toLong() }
            .filter { it > currentMs + MIN_NEXT_DISTANCE_MS }
            .minOrNull()
}
