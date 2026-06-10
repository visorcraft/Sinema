package com.sinema.util

/** In-process playback queue; survives activity recreation, dies with the process (fine for TV). */
object PlaybackQueue {
    private var ids: List<String> = emptyList()
    private var index: Int = -1

    val isActive: Boolean get() = index in ids.indices

    @Synchronized
    fun start(sceneIds: List<String>, startAt: Int) {
        ids = sceneIds.toList()
        index = startAt.coerceIn(-1, ids.size)
    }

    @Synchronized
    fun next(): String? = if (index + 1 < ids.size) ids[++index] else null.also { clearInternal() }

    @Synchronized
    fun clear() = clearInternal()

    private fun clearInternal() { ids = emptyList(); index = -1 }
}
