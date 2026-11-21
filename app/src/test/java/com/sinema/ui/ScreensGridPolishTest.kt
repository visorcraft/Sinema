package com.sinema.ui

import android.app.Activity
import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.SceneFilterOptions
import com.sinema.model.WatchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Paths

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class ScreensGridPolishTest {

    @Test
    fun `grid control visual state reflects active filters toggles and search`() {
        val defaultState = GridControlVisualState.fromSceneFilter(SceneFilterOptions())
        assertFalse(defaultState.filterActive)
        assertFalse(defaultState.watchActive)
        assertFalse(defaultState.favoritesActive)
        assertFalse(defaultState.searchActive)
        assertEquals("Search", defaultState.searchButtonLabel())

        val filtered = GridControlVisualState.fromSceneFilter(
            SceneFilterOptions(watch = WatchFilter.UNWATCHED, favoritesOnly = true),
            searchQuery = "  noir  "
        )
        assertTrue(filtered.filterActive)
        assertTrue(filtered.watchActive)
        assertTrue(filtered.favoritesActive)
        assertTrue(filtered.searchActive)
        assertEquals("Search: noir", filtered.searchButtonLabel())
    }

    @Test
    fun `applyGridControlVisuals sets activated state and search label`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(R.layout.activity_grid)

        activity.applyGridControlVisuals(
            GridControlVisualState(
                filterActive = true,
                watchActive = true,
                favoritesActive = false,
                searchQuery = "studio"
            )
        )

        assertTrue(activity.findViewById<Button>(R.id.btn_grid_filter).isActivated)
        assertTrue(activity.findViewById<Button>(R.id.btn_grid_watch).isActivated)
        assertFalse(activity.findViewById<Button>(R.id.btn_grid_fav).isActivated)
        val search = activity.findViewById<Button>(R.id.btn_grid_search)
        assertTrue(search.isActivated)
        assertEquals("Search: studio", search.text.toString())
    }

    @Test
    fun `collapsing header decision and reduce motion expand restore header cleanly`() {
        assertTrue(CollapsingHeader.nextCollapsedState(currentlyCollapsed = false, dy = 12, canScrollUp = true))
        assertTrue(CollapsingHeader.nextCollapsedState(currentlyCollapsed = true, dy = -12, canScrollUp = true))
        assertFalse(CollapsingHeader.nextCollapsedState(currentlyCollapsed = true, dy = -12, canScrollUp = false))
        assertTrue(CollapsingHeader.nextCollapsedState(currentlyCollapsed = true, dy = 0, canScrollUp = false))

        SinemaApp.instance.prefs.reduceMotion = true
        try {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val recycler = RecyclerView(activity).apply {
                layoutManager = LinearLayoutManager(activity)
            }
            val header = View(activity).apply { visibility = View.VISIBLE }
            CollapsingHeader.bind(recycler, header)
            // Simulate programmatic expand (e.g. Back to Top) after collapse
            header.visibility = View.GONE
            CollapsingHeader.expand(recycler)
            assertEquals(View.VISIBLE, header.visibility)
            assertEquals(1f, header.alpha, 0.001f)
            assertEquals(0f, header.translationY, 0.001f)
        } finally {
            SinemaApp.instance.prefs.reduceMotion = false
        }
    }

    @Test
    fun `grid layout uses rounded scroll pill header surface and active control selector`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/activity_grid.xml")))
        assertTrue(layout.contains("@drawable/screens_polish_header_surface"))
        assertTrue(layout.contains("@drawable/grid_position_background"))
        assertTrue(layout.contains("@+id/grid_controls_divider"))

        val selector = String(Files.readAllBytes(Paths.get("src/main/res/drawable/grid_control_background.xml")))
        assertTrue(selector.contains("android:state_activated=\"true\""))
        assertTrue(selector.contains("android:state_focused=\"true\""))
    }
}
