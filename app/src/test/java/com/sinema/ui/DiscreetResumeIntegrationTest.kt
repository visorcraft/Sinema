package com.sinema.ui

import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.testGraphqlErrorResponse
import com.sinema.api.withGraphqlServer
import com.sinema.model.EntityItem
import com.sinema.model.FolderItem
import com.sinema.model.GroupItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class DiscreetResumeIntegrationTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.discreetMode = false
        SinemaApp.instance.prefs.channelsEnabled = false
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
        SinemaApp.instance.updateCheckedThisSession = true
    }

    @Test
    fun `folder browser rebinds visible folder cards on discreet resume`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val activity = Robolectric.buildActivity(FolderBrowseActivity::class.java).setup().get()
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.main_frame) as FolderGridFragment
            assertDiscreetRebind(activity, fragment, FolderItem("Sensitive folder", "/private", true))
        }
    }

    @Test
    fun `root folder browser requests card rebind on discreet resume`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val activity = Robolectric.buildActivity(BrowseFoldersActivity::class.java).setup().get()
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.main_frame) as BrowseFoldersGridFragment
            assertDiscreetRebind(
                activity, fragment, FolderItem("Sensitive folder", "/private", true),
                verifyVisibleHolder = false
            )
        }
    }

    @Test
    fun `group grid rebinds actual visible cards on discreet resume`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val activity = Robolectric.buildActivity(GroupGridActivity::class.java).setup().get()
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.main_frame) as GroupGridFragment
            assertDiscreetRebind(activity, fragment, GroupItem("group", "Sensitive group", 1))
        }
    }

    @Test
    fun `entity grid rebinds actual visible cards on discreet resume`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val activity = Robolectric.buildActivity(EntityGridActivity::class.java).setup().get()
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.main_frame) as EntityGridFragment
            assertDiscreetRebind(
                activity,
                fragment,
                EntityItem("entity", "Sensitive entity", 1, null, EntityItem.Kind.TAG)
            )
        }
    }

    private fun assertDiscreetRebind(
        activity: SinemaActivity,
        fragment: VerticalGridSupportFragment,
        item: Any,
        verifyVisibleHolder: Boolean = true
    ) {
        val adapter = fragment.adapter as ArrayObjectAdapter
        adapter.clear()
        adapter.add(item)
        activity.renderGridLoadState(GridLoadState.loaded(1, "")) {}
        layout(activity)
        val card = grid(fragment.requireView()).findViewHolderForAdapterPosition(0)?.itemView
        assertNotNull(card)
        val title = card!!.findViewById<TextView>(R.id.card_title)
        assertTrue(title.text.isNotBlank())
        card.clearFocus()
        var rebinds = 0
        adapter.registerObserver(object : androidx.leanback.widget.ObjectAdapter.DataObserver() {
            override fun onChanged() {
                rebinds++
            }
        })

        fragment.onPause()
        SinemaApp.instance.prefs.discreetMode = true
        CardPresenter.discreetGeneration++
        fragment.onResume()
        assertTrue("Adapter rebind notifications: $rebinds", rebinds > 0)
        if (!verifyVisibleHolder) {
            activity.finish()
            return
        }
        layout(activity)
        val resumedCard = grid(fragment.requireView()).findViewHolderForAdapterPosition(0)?.itemView
        assertNotNull(resumedCard)
        resumedCard!!.clearFocus()
        val resumedTitle = resumedCard.findViewById<TextView>(R.id.card_title)

        assertFalse("Visible title after discreet resume: ${resumedTitle.text}", resumedTitle.text.contains("Sensitive"))
        assertEquals(View.INVISIBLE, resumedCard.findViewById<ImageView>(R.id.card_image).visibility)
        activity.finish()
    }

    private fun grid(view: View): androidx.leanback.widget.VerticalGridView {
        if (view is androidx.leanback.widget.VerticalGridView) return view
        if (view is android.view.ViewGroup) {
            (0 until view.childCount).forEach { index ->
                runCatching { grid(view.getChildAt(index)) }.getOrNull()?.let { return it }
            }
        }
        throw IllegalArgumentException("No grid in ${fragmentName(view)}")
    }

    private fun fragmentName(view: View): String = view.javaClass.simpleName

    private fun layout(activity: SinemaActivity) {
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        )
        decor.layout(0, 0, 1920, 1080)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
