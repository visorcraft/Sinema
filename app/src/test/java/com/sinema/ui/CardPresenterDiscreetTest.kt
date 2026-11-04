package com.sinema.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import com.sinema.api.testScene
import com.sinema.model.EntityItem
import com.sinema.model.GalleryItem
import com.sinema.model.GroupItem
import com.sinema.model.HomeContentRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class CardPresenterDiscreetTest {
    private lateinit var presenter: CardPresenter

    @Before
    fun setUp() {
        SinemaApp.instance.prefs.discreetMode = true
        presenter = CardPresenter(SinemaApi("http://server", "key"))
    }

    @Test
    fun `all content types hide until focused and reveal normal content on focus`() {
        val items = listOf(
            testScene(id = "s1", title = "Sensitive scene", rating100 = 100, resumeTimeSec = 20.0)
                .copy(playCount = 2) to "Sensitive scene",
            GroupItem("g1", "Sensitive group", 3, frontImagePath = "http://server/group.jpg") to "Sensitive group",
            GalleryItem("g1", "Sensitive gallery", 4, coverImageId = "cover", rating100 = 100) to "Sensitive gallery",
            EntityItem("e1", "Sensitive performer", 2, null, EntityItem.Kind.PERFORMER, rating100 = 100) to "Sensitive performer"
        )

        items.forEach { (item, normalTitle) ->
            val holder = holder()
            presenter.onBindViewHolder(holder, item)
            assertHidden(holder, normalTitle)

            assertTrue(holder.view.requestFocus())
            assertEquals(normalTitle, holder.view.findViewById<TextView>(R.id.card_title).text.toString())
            assertEquals(View.VISIBLE, holder.view.findViewById<ImageView>(R.id.card_image).visibility)
        }
    }

    @Test
    fun `recycled holder clears prior content focus artwork and overlays`() {
        val holder = holder()
        val first = GroupItem("g1", "Sensitive group", 3, frontImagePath = "http://server/group.jpg")
        presenter.onBindViewHolder(holder, first)
        holder.view.requestFocus()
        presenter.onUnbindViewHolder(holder)

        presenter.onBindViewHolder(holder, GalleryItem("g2", "Second gallery", 1))
        assertHidden(holder, "Sensitive group")
        assertFalse(holder.view.findViewById<TextView>(R.id.card_title).text.contains("Sensitive group"))
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_heart).visibility)
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_checkmark).visibility)
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_progress).visibility)
    }

    @Test
    fun `unbind clears holder text and drawable`() {
        val holder = holder()
        presenter.onBindViewHolder(holder, GroupItem("g1", "Sensitive group", 3))
        presenter.onUnbindViewHolder(holder)
        assertEquals("", holder.view.findViewById<TextView>(R.id.card_title).text.toString())
        assertEquals("", holder.view.findViewById<TextView>(R.id.card_content).text.toString())
        assertNull(holder.view.findViewById<ImageView>(R.id.card_image).drawable)
    }

    @Test
    fun `view all navigation card keeps normal labels`() {
        val holder = holder()
        val item = HomeViewAll(HomeContentRow.RECENTLY_PLAYED, "Recently Played")
        presenter.onBindViewHolder(holder, item)
        assertEquals("View All", holder.view.findViewById<TextView>(R.id.card_title).text.toString())
        assertEquals("Recently Played", holder.view.findViewById<TextView>(R.id.card_content).text.toString())
        assertEquals(View.VISIBLE, holder.view.findViewById<ImageView>(R.id.card_image).visibility)
    }

    @Test
    fun `generation change rebinds home rows containing group and entity cards`() {
        val groupAdapter = androidx.leanback.widget.ArrayObjectAdapter(presenter).apply {
            add(GroupItem("g1", "Group", 1))
        }
        val entityAdapter = androidx.leanback.widget.ArrayObjectAdapter(presenter).apply {
            add(EntityItem("e1", "Entity", 1, null, EntityItem.Kind.TAG))
        }
        val rows = androidx.leanback.widget.ArrayObjectAdapter().apply {
            add(androidx.leanback.widget.ListRow(groupAdapter))
            add(androidx.leanback.widget.ListRow(entityAdapter))
        }
        val tracker = DiscreetRebind()
        val previousGeneration = CardPresenter.discreetGeneration
        try {
            CardPresenter.discreetGeneration = previousGeneration + 1
            var rebinds = 0
            assertTrue(tracker.onResume {
                rows.rebindCardsRecursively()
                rebinds++
            })
            assertEquals(1, rebinds)
            assertFalse(tracker.onResume { rebinds++ })
        } finally {
            CardPresenter.discreetGeneration = previousGeneration
        }
    }

    private fun holder(): Presenter.ViewHolder =
        presenter.onCreateViewHolder(FrameLayout(RuntimeEnvironment.getApplication()))

    private fun assertHidden(holder: Presenter.ViewHolder, sensitiveTitle: String) {
        assertFalse(holder.view.findViewById<TextView>(R.id.card_title).text.contains(sensitiveTitle))
        assertEquals("•••", holder.view.findViewById<TextView>(R.id.card_content).text.toString())
        assertEquals(View.INVISIBLE, holder.view.findViewById<ImageView>(R.id.card_image).visibility)
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_heart).visibility)
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_checkmark).visibility)
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_progress).visibility)
    }
}
