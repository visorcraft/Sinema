package com.sinema.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.sinema.SinemaApp
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class AccessibilityFocusTransitionTest {
    @Test
    fun `accessibility ring restores original foregrounds across transitions and disable`() {
        SinemaApp.instance.prefs.highContrastFocus = true
        val activity = Robolectric.buildActivity(FocusTestActivity::class.java).setup().get()
        activity.window.decorView.apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, 1920, 1080)
        }
        val viewRoot = View::class.java.getMethod("getViewRootImpl")
            .invoke(activity.window.decorView)
        Shadow.extract<ShadowViewRootImpl>(viewRoot).callWindowFocusChanged(true)
        val original = activity.original
        val first = activity.first
        val second = activity.second

        focus(activity, first)
        assertNotSame(original, first.foreground)
        activity.refreshAccessibilityFocus()

        focus(activity, second)
        assertSame(original, first.foreground)
        assertNotSame(null, second.foreground)

        focus(activity, first)
        assertNull(second.foreground)
        activity.refreshAccessibilityFocus()

        focus(activity, second)
        assertSame(original, first.foreground)
        focus(activity, first)
        assertNull(second.foreground)

        SinemaApp.instance.prefs.highContrastFocus = false
        activity.refreshAccessibilityFocus()
        assertSame(original, first.foreground)
    }

    private fun focus(activity: FocusTestActivity, view: View) {
        val requested = view.requestFocus()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "requested=$requested shown=${view.isShown} windowFocus=${view.hasWindowFocus()} current=${view.rootView.findFocus()}",
            view.hasFocus()
        )
        shadowOf(activity).setCurrentFocus(view)
    }

    class FocusTestActivity : SinemaActivity() {
        val original = ColorDrawable(Color.CYAN)
        lateinit var first: FrameLayout
        lateinit var second: FrameLayout

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            first = FrameLayout(this).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                foreground = original
            }
            second = FrameLayout(this).apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }
            setContentView(LinearLayout(this).apply {
                addView(first, LinearLayout.LayoutParams(100, 100))
                addView(second, LinearLayout.LayoutParams(100, 100))
            })
        }
    }
}
