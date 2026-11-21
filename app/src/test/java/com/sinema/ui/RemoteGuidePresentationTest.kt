package com.sinema.ui

import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import com.sinema.SinemaApp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class RemoteGuidePresentationTest {
    @Test
    fun `guide sections have clear hierarchy and relaxed body leading`() {
        val activity = Robolectric.buildActivity(RemoteGuideActivity::class.java).setup().get()
        val scroll = activity.window.decorView.findViewWithTag<ScrollView>("remote_guide_scroll")
        val content = scroll.getChildAt(0) as LinearLayout
        assertEquals(Typeface.BOLD, content.getChildAt(1).let { (it as android.widget.TextView).typeface.style })
        assertEquals(1.25f, (content.getChildAt(2) as android.widget.TextView).lineSpacingMultiplier)
    }
}
