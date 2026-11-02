package com.sinema.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class PlaybackSaveQueue(scope: CoroutineScope) {
    private val channel = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val job = scope.launch {
        for (action in channel) action()
    }

    fun enqueue(action: suspend () -> Unit): Boolean = channel.trySend(action).isSuccess

    fun close() {
        channel.close()
    }

    suspend fun join() = job.join()
}
