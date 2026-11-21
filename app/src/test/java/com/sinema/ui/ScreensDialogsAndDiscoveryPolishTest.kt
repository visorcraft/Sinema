package com.sinema.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityItem
import com.sinema.model.EntityPlayCount
import com.sinema.model.SavedSceneFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class ScreensDialogsAndDiscoveryPolishTest {

    @Test
    fun `dialogs confirm multi choice and text input apply polished background and focus`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val confirm = Dialogs.showConfirmDialog(
            context = activity,
            title = "Delete?",
            message = "Confirm action",
            positiveLabel = "Delete",
            negativeLabel = "Cancel",
            focusNegativeByDefault = true,
            onConfirm = {}
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(confirm.window?.decorView?.background is GradientDrawable)
        assertTrue(confirm.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus())
        confirm.dismiss()

        val multi = Dialogs.showMultiChoiceDialog(
            context = activity,
            title = "Assign tags",
            items = listOf("Alpha", "Beta"),
            checked = booleanArrayOf(false, true),
            positiveLabel = "Assign",
            onConfirm = {}
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(multi.window?.decorView?.background is GradientDrawable)
        assertTrue(multi.listView.hasFocus())
        multi.dismiss()

        var submitted = ""
        val inputDialog = Dialogs.showTextInputDialog(
            context = activity,
            title = "Rename",
            initialValue = "Initial",
            hint = "Name",
            positiveLabel = "Save",
            onSubmit = { submitted = it }
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(inputDialog.window?.decorView?.background is GradientDrawable)
        inputDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Initial", submitted)
    }

    @Test
    fun `async state hides blank message during loading and frames content card`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(R.layout.activity_stats)

        activity.renderGridLoadState(GridLoadState.loading(hasContent = false)) {}
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.grid_state_overlay).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.grid_state_progress).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.grid_state_message).visibility)
        assertNotNull(activity.findViewById<View>(R.id.grid_state_card))

        activity.renderGridLoadState(GridLoadState.failed(hasContent = false, message = "Offline")) {}
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.grid_state_message).visibility)
        assertEquals("Offline", activity.findViewById<TextView>(R.id.grid_state_message).text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.grid_state_retry).visibility)
    }

    @Test
    fun `stats presenters render trend tones entity badges and saved filter cards cleanly`() {
        assertEquals(StatTrendTone.UP, statTrendToneFor("5 scenes ▲"))
        assertEquals(StatTrendTone.DOWN, statTrendToneFor("2 scenes ▼"))
        assertEquals(StatTrendTone.NEUTRAL, statTrendToneFor("42"))

        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)

        val statPresenter = StatCardPresenter()
        val statHolder = statPresenter.onCreateViewHolder(parent)
        statPresenter.onBindViewHolder(statHolder, StatCard("4 scenes ▲", "Sci-Fi"))
        val statValue = statHolder.view.findViewById<TextView>(R.id.card_title)
        assertEquals("4 scenes ▲", statValue.text.toString())
        assertEquals(
            ContextCompat.getColor(context, R.color.screens_polish_trend_up),
            statValue.currentTextColor
        )

        val entityPresenter = EntityPlayCardPresenter()
        val entityHolder = entityPresenter.onCreateViewHolder(parent)
        entityPresenter.onBindViewHolder(
            entityHolder,
            EntityPlayCount(EntityItem("1", "Studio A", 5, null, EntityItem.Kind.STUDIO), plays = 7)
        )
        assertEquals("Studio", entityHolder.view.findViewById<TextView>(R.id.stats_entity_kind).text.toString())
        assertEquals("Studio A", entityHolder.view.findViewById<TextView>(R.id.stats_entity_name).text.toString())
        assertEquals("7 plays", entityHolder.view.findViewById<TextView>(R.id.stats_entity_plays).text.toString())

        var longClicked = false
        val filterPresenter = SavedFilterCardPresenter { longClicked = true }
        val filterHolder = filterPresenter.onCreateViewHolder(parent)
        filterPresenter.onBindViewHolder(filterHolder, SavedSceneFilter(id = "f1", name = "4K Favorites"))
        assertEquals("4K Favorites", filterHolder.view.findViewById<TextView>(R.id.saved_filter_title).text.toString())
        assertTrue(filterHolder.view.performLongClick())
        assertTrue(longClicked)
        filterPresenter.onUnbindViewHolder(filterHolder)
        assertFalse(filterHolder.view.isLongClickable)
    }

    @Test
    fun `filter dialog and remote guide use legible TV focus drawables`() {
        val filterDialog = String(Files.readAllBytes(Paths.get("src/main/res/layout/dialog_scene_filter.xml")))
        assertEquals(7, Regex("@drawable/screens_polish_dialog_button_background").findAll(filterDialog).count())

        val activity = Robolectric.buildActivity(RemoteGuideActivity::class.java).setup().get()
        val scroll = activity.window.decorView.findViewWithTag<ScrollView>("remote_guide_scroll")
        val content = scroll.getChildAt(0) as LinearLayout
        val firstBody = content.getChildAt(2) as TextView
        assertTrue(firstBody.isFocusable)
        assertNotNull(firstBody.background)
    }
}
