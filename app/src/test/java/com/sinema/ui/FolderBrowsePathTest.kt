package com.sinema.ui

import android.content.Intent
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.testGraphqlErrorResponse
import com.sinema.api.withGraphqlServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class FolderBrowsePathTest {
    @Before
    fun configureApp() {
        SinemaApp.instance.prefs.discreetMode = false
        SinemaApp.instance.prefs.channelsEnabled = false
        SinemaApp.instance.pinVerifiedThisSession = true
        SinemaApp.instance.decoySession = false
        SinemaApp.instance.updateCheckedThisSession = true
    }

    @Test
    fun `path label keeps every character and emphasizes the current folder`() {
        val text = FolderPathLabel.text("/data/library/Season 01")
        val spanned = text as Spanned
        assertEquals("/data/library/Season 01", text.toString().replace("\u200B", ""))
        val current = spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java)
            .first { spanned.getSpanEnd(it) == spanned.length }
        assertEquals(0xFFFFFFFF.toInt(), current.foregroundColor)
        val parents = spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java)
            .filter { it.foregroundColor != 0xFFFFFFFF.toInt() && it.foregroundColor != 0xFF8E8E8E.toInt() }
        assertTrue(parents.isNotEmpty())

        val longName = "a".repeat(80)
        val longPath = FolderPathLabel.text("/data/$longName").toString()
        assertEquals("/data/$longName", longPath.replace("\u200B", ""))
        assertTrue(longPath.contains(longName))
        assertFalse(longPath.contains("a\u200B"))
    }

    @Test
    fun `open folder shows the full path and not a second title`() {
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val intent = Intent(RuntimeEnvironment.getApplication(), FolderBrowseActivity::class.java)
                .putExtra("path", "/data/library/Season 01")
            val activity = Robolectric.buildActivity(FolderBrowseActivity::class.java, intent).setup().get()
            shadowOf(Looper.getMainLooper()).idle()
            val path = activity.findViewById<TextView>(R.id.folder_path)
            assertEquals(View.VISIBLE, path.visibility)
            assertEquals("/data/library/Season 01", path.contentDescription)
            assertEquals("/data/library/Season 01", path.text.toString().replace("\u200B", ""))
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.main_frame) as FolderGridFragment
            assertFalse(fragment.isShowingTitle)
            val sort = activity.findViewById<android.widget.Button>(R.id.btn_grid_sort)
            assertTrue(sort.text.toString().startsWith("Sort:"))
        }
    }

    @Test
    fun `discreet mode hides the folder path and uses a neutral title`() {
        SinemaApp.instance.prefs.discreetMode = true
        withGraphqlServer(testGraphqlErrorResponse("offline"), configureSinemaApp = true) { _, _ ->
            val intent = Intent(RuntimeEnvironment.getApplication(), FolderBrowseActivity::class.java)
                .putExtra("path", "/data/library/Season 01")
            val activity = Robolectric.buildActivity(FolderBrowseActivity::class.java, intent).setup().get()
            shadowOf(Looper.getMainLooper()).idle()
            val path = activity.findViewById<TextView>(R.id.folder_path)
            assertEquals(View.GONE, path.visibility)
            assertEquals("", path.text.toString())
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.main_frame) as FolderGridFragment
            assertTrue(fragment.isShowingTitle)
            assertEquals("Folder", fragment.title.toString())
        }
    }
}
