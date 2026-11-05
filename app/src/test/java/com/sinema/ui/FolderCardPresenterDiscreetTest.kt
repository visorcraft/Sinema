package com.sinema.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.FolderItem
import com.sinema.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class FolderCardPresenterDiscreetTest {
    private val presenter = FolderCardPresenter()

    @Before
    fun setUp() {
        SinemaApp.instance.prefs.discreetMode = true
    }

    @Test
    fun `folder and image content hides until focused and reveals on focus`() {
        val items = listOf(
            FolderItem(
                "Sensitive folder", "/private", true, childCount = 2,
                firstSceneId = "s1", hasFavorites = true
            ) to "Sensitive folder",
            FolderItem(
                "Sensitive image", "/private/a.jpg", false,
                image = ImageItem("i1", "Private", "/a.jpg", width = 10, height = 20)
            ) to "Sensitive image",
            ImageItem("i1", "Sensitive standalone", "/private/a.jpg", width = 10, height = 20, rating100 = 100) to "Sensitive standalone"
        )

        items.forEach { (item, normalTitle) ->
            val holder = holder()
            presenter.onBindViewHolder(holder, item)
            assertHidden(holder, normalTitle)

            assertTrue(holder.view.requestFocus())
            assertTrue(holder.view.findViewById<TextView>(R.id.card_title).text.contains(normalTitle))
            assertEquals(View.VISIBLE, holder.view.findViewById<ImageView>(R.id.card_image).visibility)
        }
    }

    @Test
    fun `recycled folder holder cannot leak previous item`() {
        val holder = holder()
        presenter.onBindViewHolder(
            holder,
            FolderItem("Sensitive folder", "/private", true, childCount = 2, firstSceneId = "s1", hasFavorites = true)
        )
        holder.view.requestFocus()
        presenter.onUnbindViewHolder(holder)
        presenter.onBindViewHolder(holder, ImageItem("i2", "Second image", "/second.jpg"))

        assertHidden(holder, "Sensitive folder")
        assertFalse(holder.view.findViewById<TextView>(R.id.card_title).text.contains("Sensitive folder"))
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_heart).visibility)
    }

    @Test
    fun `unbind clears folder text and drawable`() {
        val holder = holder()
        presenter.onBindViewHolder(holder, ImageItem("i1", "Sensitive image", "/a.jpg"))
        presenter.onUnbindViewHolder(holder)
        assertEquals("", holder.view.findViewById<TextView>(R.id.card_title).text.toString())
        assertEquals("", holder.view.findViewById<TextView>(R.id.card_content).text.toString())
        assertNull(holder.view.findViewById<ImageView>(R.id.card_image).drawable)
    }

    @Test
    fun `bind and unbind after host activity destruction do not crash`() {
        val host = Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java).setup()
        val holder = presenter.onCreateViewHolder(FrameLayout(host.get()))
        val item = ImageItem("i1", "Sensitive image", "/a.jpg")
        presenter.onBindViewHolder(holder, item)
        host.destroy()
        assertTrue(host.get().isDestroyed)

        presenter.onBindViewHolder(holder, item)
        presenter.onUnbindViewHolder(holder)

        assertNull(holder.view.findViewById<ImageView>(R.id.card_image).drawable)
    }

    private fun holder(): Presenter.ViewHolder =
        presenter.onCreateViewHolder(FrameLayout(RuntimeEnvironment.getApplication()))

    private fun assertHidden(holder: Presenter.ViewHolder, sensitiveTitle: String) {
        assertFalse(holder.view.findViewById<TextView>(R.id.card_title).text.contains(sensitiveTitle))
        assertEquals("•••", holder.view.findViewById<TextView>(R.id.card_content).text.toString())
        assertEquals(View.INVISIBLE, holder.view.findViewById<ImageView>(R.id.card_image).visibility)
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_heart).visibility)
    }
}
