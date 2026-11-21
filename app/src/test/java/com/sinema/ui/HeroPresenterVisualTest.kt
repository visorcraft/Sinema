package com.sinema.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import com.sinema.api.testScene
import com.sinema.model.StudioRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class HeroPresenterVisualTest {
    @Before
    fun setUp() {
        SinemaApp.instance.prefs.reduceMotion = true
    }

    @Test
    fun `resumed hero renders eyebrow quality badge progress bar and focus-reactive CTA`() {
        val context = RuntimeEnvironment.getApplication()
        val presenter = HeroPresenter(SinemaApi("http://server", "key"))
        val parent = FrameLayout(context)
        val holder = presenter.onCreateViewHolder(parent)
        val view = holder.view
        parent.addView(view)

        val scene = testScene(id = "s1", title = "Night Run", rating100 = 100)
            .copy(duration = 1200.0, width = 3840, height = 2160, studio = StudioRef("st1", "Studio A"))
        presenter.onBindViewHolder(holder, HeroItem(scene, resumeMs = 480_000L))

        assertTrue("Hero clips artwork to rounded corners", view.clipToOutline)
        assertEquals("CONTINUE WATCHING", view.findViewById<TextView>(R.id.hero_eyebrow).text.toString())
        assertEquals(View.VISIBLE, view.findViewById<TextView>(R.id.hero_badge).visibility)
        assertEquals("4K", view.findViewById<TextView>(R.id.hero_badge).text.toString())

        val progress = view.findViewById<ProgressBar>(R.id.hero_progress)
        assertEquals(View.VISIBLE, progress.visibility)
        assertEquals(40, progress.progress)
        assertNotNull(progress.contentDescription)

        val action = view.findViewById<TextView>(R.id.hero_action)
        assertEquals("▶ RESUME  8:00", action.text.toString())
        assertEquals(
            ContextCompat.getColor(context, R.color.home_polish_hero_cta_text_unfocused),
            action.currentTextColor
        )
        val unfocusedZ = view.translationZ

        assertTrue(view.requestFocus())
        assertTrue("Focused hero must lift above resting Z", view.translationZ > unfocusedZ)
        assertEquals(
            ContextCompat.getColor(context, R.color.home_polish_hero_cta_text_focused),
            action.currentTextColor
        )
        assertTrue(view.contentDescription.toString().contains("Night Run"))

        presenter.onUnbindViewHolder(holder)
        assertEquals(0f, view.translationZ, 0.001f)
        assertEquals(View.GONE, progress.visibility)
        assertEquals(View.GONE, view.findViewById<TextView>(R.id.hero_badge).visibility)
        assertNull(view.contentDescription)
    }

    @Test
    fun `featured hero without resume hides progress bar and shows featured eyebrow`() {
        val context = RuntimeEnvironment.getApplication()
        val presenter = HeroPresenter(SinemaApi("http://server", "key"))
        val holder = presenter.onCreateViewHolder(FrameLayout(context))
        val scene = testScene(id = "s2", title = "Fresh Pick")
            .copy(duration = 600.0, width = 640, height = 360)

        presenter.onBindViewHolder(holder, HeroItem(scene, resumeMs = 0L))

        assertEquals("FEATURED", holder.view.findViewById<TextView>(R.id.hero_eyebrow).text.toString())
        assertEquals(View.GONE, holder.view.findViewById<TextView>(R.id.hero_badge).visibility)
        assertEquals(View.GONE, holder.view.findViewById<ProgressBar>(R.id.hero_progress).visibility)
        assertEquals("▶ PLAY", holder.view.findViewById<TextView>(R.id.hero_action).text.toString())
    }
}
