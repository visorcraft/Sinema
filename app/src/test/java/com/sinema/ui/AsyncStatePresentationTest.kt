package com.sinema.ui

import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import com.sinema.R
import com.sinema.SinemaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Paths

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class AsyncStatePresentationTest {
    @Test
    fun `async state improves hierarchy and retry presentation`() {
        val layout = String(Files.readAllBytes(Paths.get("src/main/res/layout/view_async_state.xml")))
        assertTrue(layout.contains("android:maxWidth=\"720dp\""))
        assertTrue(layout.contains("android:textSize=\"20sp\""))
        assertTrue(layout.contains("android:lineSpacingExtra=\"4dp\""))
        assertTrue(layout.contains("@drawable/async_state_retry_background"))
    }

    @Test
    fun `retry uses dark text on its cyan background`() {
        val context = RuntimeEnvironment.getApplication()
        val state = LayoutInflater.from(context)
            .inflate(R.layout.view_async_state, FrameLayout(context), false)
        val retry = state.findViewById<Button>(R.id.grid_state_retry)

        assertEquals(0xFF0E1A22.toInt(), retry.currentTextColor)
    }
}
