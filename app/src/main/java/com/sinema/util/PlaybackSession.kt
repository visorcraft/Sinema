package com.sinema.util

import com.google.gson.Gson

internal data class PlaybackSession(
    val profileId: String,
    val sceneId: String,
    val title: String,
    val positionMs: Long,
    val queueItems: List<PlaybackQueue.Item>,
    val queueIndex: Int
)

internal object PlaybackSessionCodec {
    private data class Envelope(val version: Int = 1, val session: PlaybackSession)

    private val gson = Gson()

    fun encode(session: PlaybackSession): String = gson.toJson(Envelope(session = session))

    fun decode(json: String): PlaybackSession? {
        return try {
            val envelope = gson.fromJson(json, Envelope::class.java) ?: return null
            if (envelope.version != 1) return null
            val session = envelope.session ?: return null
            requireNotNull(session.profileId)
            requireNotNull(session.title)
            requireNotNull(session.queueItems)
            require(session.sceneId.isNotBlank())
            require(session.positionMs >= 0L)
            session.queueItems.forEach {
                requireNotNull(it.title)
                require(it.id.isNotBlank())
            }
            if (session.queueItems.isEmpty()) {
                require(session.queueIndex == -1)
            } else {
                require(session.queueIndex in session.queueItems.indices)
                require(session.queueItems[session.queueIndex].id == session.sceneId)
            }
            session.copy(positionMs = session.positionMs.coerceAtLeast(0L))
        } catch (_: Exception) {
            null
        }
    }
}

internal object PlaybackSessionRestore {
    fun isEligible(
        session: PlaybackSession?,
        activeProfileId: String,
        hasPin: Boolean,
        pinVerified: Boolean,
        incognito: Boolean
    ): Boolean = session != null &&
        (session.positionMs > 0L || session.queueItems.isNotEmpty()) &&
        session.profileId == activeProfileId &&
        !incognito &&
        (!hasPin || pinVerified)

    fun shouldPersist(positionMs: Long): Boolean = positionMs >= 5_000L
}
