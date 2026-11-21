package com.sinema.ui

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.testScene
import com.sinema.model.ImageItem
import com.sinema.util.SceneIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Paths

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class SceneDetailAndImageViewerPolishTest {

    @Test
    fun `scene detail reflects active favorite watched and tinted star states`() {
        val context = RuntimeEnvironment.getApplication()
        val scene = testScene(id = "s9").copy(
            playCount = 2,
            rating100 = 80,
            details = "Long synopsis text for expansion testing"
        )
        val controller = Robolectric.buildActivity(
            SceneDetailActivity::class.java,
            SceneIntents.detail(context, scene)
        ).create()
        val activity = controller.get()

        assertTrue(activity.findViewById<Button>(R.id.btn_favorite).isActivated)
        assertTrue(activity.findViewById<Button>(R.id.btn_mark_watched).isActivated)

        val filledColor = ContextCompat.getColor(activity, R.color.screens_polish_star_filled)
        val emptyColor = ContextCompat.getColor(activity, R.color.screens_polish_star_empty)
        val star4 = activity.findViewById<Button>(R.id.btn_star_4)
        val star5 = activity.findViewById<Button>(R.id.btn_star_5)
        assertTrue(star4.isActivated)
        assertEquals(filledColor, star4.currentTextColor)
        assertFalse(star5.isActivated)
        assertEquals(emptyColor, star5.currentTextColor)

        val synopsis = activity.findViewById<TextView>(R.id.detail_details)
        synopsis.performClick()
        assertTrue(synopsis.isActivated)
        assertEquals(Int.MAX_VALUE, synopsis.maxLines)
        synopsis.performClick()
        assertFalse(synopsis.isActivated)
        assertEquals(3, synopsis.maxLines)

        controller.destroy()
    }

    @Test
    fun `scene detail layout uses dedicated hero scrim and interactive pill drawables`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_scene_detail.xml")))
        assertTrue(layout.contains("@drawable/scene_detail_hero_scrim"))
        assertTrue(layout.contains("@drawable/scene_detail_pill_background"))
        assertTrue(layout.contains("@drawable/scene_detail_synopsis_background"))
    }

    @Test
    fun `image viewer state computes HUD progress status and contextual hint modes`() {
        val base = ImageViewerState(position = 2)
        assertEquals(600, base.progressPermille(totalCount = 5))
        assertEquals(0, base.progressPermille(totalCount = 1))
        assertEquals(ImageViewerHintMode.BROWSE, base.hintMode(slideshowEnabled = false))
        assertEquals(ImageViewerHintMode.SLIDESHOW, base.hintMode(slideshowEnabled = true))

        val zoomed = base.zoomIn()
        assertTrue(zoomed.isZoomed)
        assertEquals(ImageViewerHintMode.ZOOMED, zoomed.hintMode(slideshowEnabled = false))
        assertEquals(
            "Fit screen  •  125%  •  Shuffle  •  Slideshow 5s",
            zoomed.statusSummary(shuffleEnabled = true, slideshowEnabled = true, slideshowDelayMs = 5_000L)
        )
    }

    @Test
    fun `image viewer binds scrim progress bar and contextual zoom hint`() {
        val context = RuntimeEnvironment.getApplication()
        val images = listOf(
            ImageItem(id = "1", title = "One", path = "/1.jpg"),
            ImageItem(id = "2", title = "Two", path = "/2.jpg"),
            ImageItem(id = "3", title = "Three", path = "/3.jpg"),
            ImageItem(id = "4", title = "Four", path = "/4.jpg")
        )
        val controller = Robolectric.buildActivity(
            ImageViewActivity::class.java,
            ImageViewActivity.intentFor(context, images, startIndex = 1)
        ).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()

        val progress = activity.findViewById<ProgressBar>(R.id.image_progress_bar)
        val hint = activity.findViewById<TextView>(R.id.image_hint)
        val scrim = activity.findViewById<View>(R.id.image_controls_scrim)

        assertEquals(View.VISIBLE, scrim.visibility)
        assertEquals(View.VISIBLE, progress.visibility)
        assertEquals(500, progress.progress)
        assertEquals(activity.getString(R.string.screens_polish_image_hint_browse), hint.text.toString())

        activity.onKeyDown(android.view.KeyEvent.KEYCODE_ZOOM_IN, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(activity.getString(R.string.screens_polish_image_hint_zoomed), hint.text.toString())

        controller.pause().stop().destroy()
    }
}
