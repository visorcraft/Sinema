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
        val longPath = FolderPathLabel.text("/data/$longName")
        assertEquals("/data/$longName", longPath.toString().replace("\u200B", ""))
        assertTrue(longPath.toString().contains("\u200B"))
    }

    @Test
    fun `open folder shows the full path and titles the screen with the folder name`() {
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
            val title = fragment.title.toString()
            assertTrue(title.startsWith("Season 01"))
            assertFalse(title.contains("/data/library"))
        }
    }
}
