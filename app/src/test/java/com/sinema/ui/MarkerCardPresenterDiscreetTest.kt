package com.sinema.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import com.sinema.model.SceneMarkerItem
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
class MarkerCardPresenterDiscreetTest {
    private val presenter = MarkerCardPresenter(SinemaApi("http://server", "key"))

    @Before
    fun setUp() {
        SinemaApp.instance.prefs.discreetMode = true
    }

    @Test
    fun `marker hides until focused and reveals on focus`() {
        val holder = holder()
        presenter.onBindViewHolder(holder, marker("m1", "Sensitive marker"))
        assertHidden(holder, "Sensitive marker")

        assertTrue(holder.view.requestFocus())
        assertEquals("Sensitive marker", holder.view.findViewById<TextView>(R.id.card_title).text.toString())
        assertTrue(holder.view.findViewById<TextView>(R.id.card_content).text.contains("Sensitive scene"))
        assertEquals(View.VISIBLE, holder.view.findViewById<ImageView>(R.id.card_image).visibility)
    }

    @Test
    fun `recycled marker holder cannot leak previous marker`() {
        val holder = holder()
        presenter.onBindViewHolder(holder, marker("m1", "Sensitive marker"))
        holder.view.requestFocus()
        presenter.onUnbindViewHolder(holder)
        holder.view.clearFocus()
        presenter.onBindViewHolder(holder, marker("m2", "Second marker"))

        assertHidden(holder, "Sensitive marker")
        assertFalse(holder.view.findViewById<TextView>(R.id.card_title).text.contains("Sensitive marker"))
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_heart).visibility)
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_checkmark).visibility)
        assertEquals(View.GONE, holder.view.findViewById<View>(R.id.card_progress).visibility)
    }

    @Test
    fun `unbind clears marker text and drawable`() {
        val holder = holder()
        presenter.onBindViewHolder(holder, marker("m1", "Sensitive marker"))
        presenter.onUnbindViewHolder(holder)
        assertEquals("", holder.view.findViewById<TextView>(R.id.card_title).text.toString())
        assertEquals("", holder.view.findViewById<TextView>(R.id.card_content).text.toString())
        assertNull(holder.view.findViewById<ImageView>(R.id.card_image).drawable)
    }

    private fun marker(id: String, title: String) = SceneMarkerItem(
        id, title, 12.0, "s1", "Sensitive scene", 1920, 1080, "t1", "Tag"
    )

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
