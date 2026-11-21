package com.sinema.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import com.sinema.api.testScene
import com.sinema.model.InterfaceTextScale
import com.sinema.model.StudioRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class CardVisualsTest {
    @Before
    fun setUp() {
        SinemaApp.instance.prefs.discreetMode = false
        SinemaApp.instance.prefs.reduceMotion = true
    }

    @Test
    fun `scene metadata and badges reflect rating progress watched and quality states`() {
        val inProgress = testScene(id = "s1", rating100 = 80, resumeTimeSec = 300.0)
            .copy(duration = 1000.0, width = 3840, height = 2160, oCounter = 2)
        assertEquals("16:40", CardVisuals.sceneDurationBadge(inProgress))
        assertEquals("4K", CardVisuals.sceneQualityBadge(inProgress))
        val progressMeta = CardVisuals.sceneMetadataLine(inProgress)
        assertTrue(progressMeta.contains("★★★★"))
        assertTrue(progressMeta.contains("30%"))
        assertTrue(progressMeta.contains("12m left"))
        assertTrue(progressMeta.contains("O×2"))

        val watched = testScene(id = "s2").copy(duration = 600.0, playCount = 3)
        assertEquals("Watched", CardVisuals.sceneMetadataLine(watched))

        val unwatchedWithStudio = testScene(id = "s3")
            .copy(duration = 600.0, studio = StudioRef("st1", "Studio Nine"))
        assertEquals("Studio Nine", CardVisuals.sceneMetadataLine(unwatchedWithStudio))

        val plainUnwatched = testScene(id = "s4").copy(duration = 600.0, studio = null)
        assertEquals("10:00", CardVisuals.sceneMetadataLine(plainUnwatched))
    }

    @Test
    fun `scene card elevates bottom badges above progress bar and resets cleanly on unbind`() {
        val context = RuntimeEnvironment.getApplication()
        val presenter = CardPresenter(SinemaApi("http://server", "key"))
        val parent = FrameLayout(context)
        val holder = presenter.onCreateViewHolder(parent)
        val view = holder.view
        parent.addView(view)

        assertTrue(view.findViewById<View>(R.id.card_artwork_frame).clipToOutline)

        val partialWatched = testScene(id = "s1", title = "Halfway", resumeTimeSec = 400.0)
            .copy(duration = 1000.0, playCount = 1, width = 1920, height = 1080)
        presenter.onBindViewHolder(holder, partialWatched)

        val durationBadge = view.findViewById<TextView>(R.id.card_duration_badge)
        val qualityBadge = view.findViewById<TextView>(R.id.card_quality_badge)
        val checkmark = view.findViewById<TextView>(R.id.card_checkmark)
        val scrim = view.findViewById<View>(R.id.card_artwork_scrim)

        assertEquals(View.VISIBLE, durationBadge.visibility)
        assertEquals("16:40", durationBadge.text.toString())
        assertEquals(View.VISIBLE, qualityBadge.visibility)
        assertEquals("1080p", qualityBadge.text.toString())
        assertEquals(View.VISIBLE, scrim.visibility)

        val expectedClearedMargin = context.resources.getDimensionPixelSize(
            R.dimen.card_polish_badge_margin_above_progress
        )
        val defaultMargin = context.resources.getDimensionPixelSize(R.dimen.card_polish_badge_margin)
        assertEquals(expectedClearedMargin, (checkmark.layoutParams as FrameLayout.LayoutParams).bottomMargin)
        assertEquals(expectedClearedMargin, (durationBadge.layoutParams as FrameLayout.LayoutParams).bottomMargin)

        assertTrue(view.requestFocus())
        assertTrue("Focused card must lift in Z", view.translationZ > 0f)
        assertEquals(Typeface.BOLD, view.findViewById<TextView>(R.id.card_title).typeface.style)

        presenter.onUnbindViewHolder(holder)
        assertEquals(0f, view.translationZ, 0.001f)
        assertEquals(View.GONE, durationBadge.visibility)
        assertEquals(View.GONE, qualityBadge.visibility)
        assertEquals(View.GONE, scrim.visibility)
        assertEquals(defaultMargin, (checkmark.layoutParams as FrameLayout.LayoutParams).bottomMargin)
    }

    @Test
    fun `discreet mode hides new scene badges and scrim until focused`() {
        SinemaApp.instance.prefs.discreetMode = true
        val context = RuntimeEnvironment.getApplication()
        val presenter = CardPresenter(SinemaApi("http://server", "key"))
        val parent = FrameLayout(context)
        val holder = presenter.onCreateViewHolder(parent)
        val view = holder.view
        parent.addView(view)

        val scene = testScene(id = "s1", title = "Private Scene", resumeTimeSec = 300.0)
            .copy(duration = 900.0, width = 3840, height = 2160)
        presenter.onBindViewHolder(holder, scene)

        assertEquals(View.GONE, view.findViewById<View>(R.id.card_artwork_scrim).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.card_duration_badge).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.card_quality_badge).visibility)

        assertTrue(view.requestFocus())
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.card_artwork_scrim).visibility)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.card_duration_badge).visibility)
        assertEquals("4K", view.findViewById<TextView>(R.id.card_quality_badge).text.toString())
    }

    @Test
    fun `shortcut cards use curated tinted surfaces instead of flat gray`() {
        val context = RuntimeEnvironment.getApplication()
        val presenter = SettingsPresenter()
        val holder = presenter.onCreateViewHolder(FrameLayout(context))
        presenter.onBindViewHolder(holder, "Retry Connection")
        val image = holder.view.findViewById<ImageView>(R.id.card_image)
        assertNotNull(ImageViewCompat.getImageTintList(image))
        assertNotNull(image.background)
        assertEquals(CardVisuals.SurfaceTone.RETRY, CardVisuals.shortcutToneFor("Retry Connection"))
        assertEquals(CardVisuals.SurfaceTone.WARM, CardVisuals.shortcutToneFor("Favorites"))
        assertEquals(CardVisuals.SurfaceTone.ACCENT, CardVisuals.shortcutToneFor("Search"))
    }

    @Test
    fun `command pill applies distinct focus tint lifts in Z and contains text at max scale`() {
        val base = RuntimeEnvironment.getApplication()
        val config = Configuration(base.resources.configuration).apply {
            fontScale = InterfaceTextScale.EXTRA_LARGE.fontScale
        }
        val maxContext = base.createConfigurationContext(config)
        val presenter = CommandPresenter(hasPin = true)
        val parent = FrameLayout(maxContext)
        val logOutHolder = presenter.onCreateViewHolder(parent)
        val refreshHolder = presenter.onCreateViewHolder(parent)
        presenter.onBindViewHolder(logOutHolder, HomeCommandRow.LOG_OUT)
        presenter.onBindViewHolder(refreshHolder, HomeCommandRow.REFRESH)
        parent.addView(logOutHolder.view)
        parent.addView(refreshHolder.view)

        assertTrue(logOutHolder.view.requestFocus())
        assertTrue(logOutHolder.view.translationZ > 0f)
        val logOutIconTint = ImageViewCompat.getImageTintList(
            logOutHolder.view.findViewById(R.id.command_icon)
        )?.defaultColor
        assertEquals(
            ContextCompat.getColor(maxContext, R.color.home_polish_command_logout_focused),
            logOutIconTint
        )

        assertTrue(refreshHolder.view.requestFocus())
        val refreshIconTint = ImageViewCompat.getImageTintList(
            refreshHolder.view.findViewById(R.id.command_icon)
        )?.defaultColor
        assertNotEquals(logOutIconTint, refreshIconTint)

        val pill = logOutHolder.view
        pill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        pill.layout(0, 0, pill.measuredWidth, pill.measuredHeight)
        val title = pill.findViewById<TextView>(R.id.command_title)
        assertTrue(title.right <= pill.width - pill.paddingEnd)
        assertTrue(title.bottom <= pill.height - pill.paddingBottom)

        presenter.onUnbindViewHolder(logOutHolder)
        assertEquals(0f, logOutHolder.view.translationZ, 0.001f)
        assertNull(ImageViewCompat.getImageTintList(logOutHolder.view.findViewById(R.id.command_icon)))
    }

    @Test
    fun `home order screen cues boundary buttons while keeping them focusable`() {
        val intent = Intent(RuntimeEnvironment.getApplication(), HomeOrderActivity::class.java)
            .putExtra(HomeOrderActivity.EXTRA_MODE, HomeOrderActivity.MODE_SHORTCUTS)
        val controller = Robolectric.buildActivity(HomeOrderActivity::class.java, intent).setup()
        val activity = controller.get()
        val order = SinemaApp.instance.prefs.homeLayout.shortcutOrder.orEmpty()
        assertTrue(order.size >= 2)

        val firstUp = activity.window.decorView.findViewWithTag<Button>("up:shortcut:${order.first().name}")
        val lastDown = activity.window.decorView.findViewWithTag<Button>("down:shortcut:${order.last().name}")
        assertNotNull(firstUp)
        assertNotNull(lastDown)
        assertTrue(firstUp.isFocusable)
        assertTrue(lastDown.isFocusable)
        assertTrue("First Move Up button should show muted boundary cue", firstUp.alpha < 1f)
        assertTrue("Last Move Down button should show muted boundary cue", lastDown.alpha < 1f)
        controller.destroy()
    }
}
