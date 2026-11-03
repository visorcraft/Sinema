package com.sinema.ui

import android.os.Bundle
import android.view.KeyEvent
import com.sinema.R

/**
 * Template for the standard grid screens: the activity_grid layout, one
 * sortable grid fragment (created once, restored from the fragment manager
 * afterwards), grid-control binding, and Menu-key routing.
 *
 * Subclasses implement [createGridFragment]. Headers and custom controls
 * override [onGridBound], which runs on every create so restored fragments
 * keep the behavior.
 */
abstract class GridActivity : SinemaActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        val screen = supportFragmentManager.findFragmentById(R.id.main_frame) as? SceneGridFragment
            ?: createGridFragment().also { fragment ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.main_frame, fragment)
                    .commit()
            }
        bindGridControls(screen)
        onGridBound(screen)
    }

    /** Builds the screen's grid fragment; called only on first creation. */
    protected abstract fun createGridFragment(): SceneGridFragment

    /** Post-bind hook for headers and custom controls; runs on every create. */
    protected open fun onGridBound(screen: SceneGridFragment) {}

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)
}
