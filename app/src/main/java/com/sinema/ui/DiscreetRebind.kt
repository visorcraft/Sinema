package com.sinema.ui

import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow

/**
 * Shared discreet-mode rebind tracking: compares CardPresenter's generation
 * on resume and triggers a visible-card rebind (no data reload) when the
 * setting flipped while the screen was away.
 */
class DiscreetRebind {
    private var boundGeneration = CardPresenter.discreetGeneration

    /** @return true when the generation changed and [rebind] ran. */
    fun onResume(rebind: () -> Unit): Boolean {
        if (boundGeneration == CardPresenter.discreetGeneration) return false
        boundGeneration = CardPresenter.discreetGeneration
        rebind()
        return true
    }
}

/** Rebinds visible cards directly (flat adapters) or via ListRow children. */
fun ArrayObjectAdapter.rebindCardsRecursively() {
    if (size() == 0) return
    (0 until size()).forEach { i ->
        when (val item = get(i)) {
            is ListRow -> (item.adapter as? ArrayObjectAdapter)?.rebindCardsRecursively()
            else -> Unit
        }
    }
    notifyItemRangeChanged(0, size())
}
