package com.sinema.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import com.sinema.SinemaApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class DialogsPresentationTest {
    @Test
    fun `choice dialog has polished background and focused list`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dialog = Dialogs.showChoiceDialog(activity, "Sort", listOf("Recent", "Title"), "Recent", { it }, {})
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(dialog.window?.decorView?.background)
        assertTrue(dialog.window?.decorView?.background is GradientDrawable)
        assertTrue(dialog.listView.hasFocus())
    }
}
