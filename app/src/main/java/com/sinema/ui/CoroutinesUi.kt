package com.sinema.ui

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 *launch + standard error handling: rethrow cancellation, log with the
 * Sinema tag, and toast the failure — the pattern repeated across nearly
 * every screen. Not for blocks that need custom recovery logic.
 */
fun FragmentActivity.launchSafely(
    label: String,
    onError: ((Exception) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("Sinema", "$label failed", e)
        onError?.invoke(e) ?: run {
            Toast.makeText(this@launchSafely, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

/**[launchSafely] for fragments; guards toasts against detachment. */
fun Fragment.launchSafely(
    label: String,
    onError: ((Exception) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("Sinema", "$label failed", e)
        val ctx: Context? = context
        if (onError != null) {
            onError(e)
        } else if (ctx != null) {
            Toast.makeText(ctx, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
