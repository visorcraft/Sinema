package com.sinema.ui

import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import com.sinema.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommandPresenterTest {
    @Test
    fun `log out is focusable and announces lock and exit`() {
        val view = bind(CommandPresenter(hasPin = true), HomeCommandRow.LOG_OUT)
        assertTrue(view.isFocusable)
        assertEquals(view.context.getString(R.string.accessibility_log_out), view.contentDescription)
        assertEquals(view.id, view.nextFocusLeftId)
    }

    @Test
    fun `about blocks right edge wrapping`() {
        val view = bind(CommandPresenter(hasPin = true), HomeCommandRow.ABOUT)
        assertEquals(view.id, view.nextFocusRightId)
    }

    @Test
    fun `refresh is the left edge without a pin but remains navigable with one`() {
        val withoutPin = bind(CommandPresenter(hasPin = false), HomeCommandRow.REFRESH)
        val withPin = bind(CommandPresenter(hasPin = true), HomeCommandRow.REFRESH)
        assertEquals(withoutPin.id, withoutPin.nextFocusLeftId)
        assertEquals(View.NO_ID, withPin.nextFocusLeftId)
    }

    @Test
    fun `dpad down invokes the explicit browse callback`() {
        var calls = 0
        val view = bind(CommandPresenter(hasPin = true, onDown = { calls++ }), HomeCommandRow.REFRESH)
        view.layout(0, 0, 156, 64)
        view.requestFocus()
        val handled = view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(handled)
        assertEquals(1, calls)
        assertFalse(view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
    }

    private fun bind(presenter: CommandPresenter, command: String): android.view.View {
        val parent = FrameLayout(RuntimeEnvironment.getApplication())
        val holder = presenter.onCreateViewHolder(parent)
        presenter.onBindViewHolder(holder, command)
        return holder.view
    }
}
