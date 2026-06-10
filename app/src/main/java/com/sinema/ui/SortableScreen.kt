package com.sinema.ui

import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.sinema.R

/** A fragment that can show a sort picker; hosts forward the remote's MENU key here. */
interface SortableScreen {
    fun showSortDialog()
}

/**
 * Routes KEYCODE_MENU to the hosted SortableScreen fragment.
 * On-device fallback for remotes without a MENU key is deferred.
 */
fun FragmentActivity.handleSortMenuKey(keyCode: Int): Boolean {
    if (keyCode != KeyEvent.KEYCODE_MENU) return false
    (supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen)?.showSortDialog()
    return true
}
