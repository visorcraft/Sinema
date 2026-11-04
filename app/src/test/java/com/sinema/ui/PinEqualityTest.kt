package com.sinema.ui

import android.content.Intent
import android.widget.Button
import android.widget.TextView
import com.sinema.R
import com.sinema.SinemaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class PinEqualityTest {
    private val app get() = SinemaApp.instance

    @Before
    fun setUp() {
        app.pinVerifiedThisSession = true
        app.decoySession = false
        app.prefs.removePin()
        app.prefs.removeDecoyPin()
    }

    @Test
    fun `primary pin cannot duplicate existing decoy`() {
        app.prefs.setDecoyPinHash("1234")
        val activity = launch(decoySetup = false)
        val snapshotBefore = app.prefs.exportSnapshot()

        enter(activity, "1234")
        enter(activity, "1234")

        assertDuplicateRejected(activity, snapshotBefore)
        assertTrue(app.prefs.verifyDecoyPin("1234"))
    }

    @Test
    fun `decoy pin cannot duplicate existing primary`() {
        app.prefs.setPinHash("1234")
        val activity = launch(decoySetup = true)
        val snapshotBefore = app.prefs.exportSnapshot()

        enter(activity, "1234")
        enter(activity, "1234")

        assertDuplicateRejected(activity, snapshotBefore)
        assertTrue(app.prefs.verifyPin("1234"))
    }

    @Test
    fun `different decoy succeeds without changing primary`() {
        app.prefs.setPinHash("1234")
        val activity = launch(decoySetup = true)

        enter(activity, "5678")
        enter(activity, "5678")

        assertTrue(activity.isFinishing)
        assertTrue(app.prefs.verifyDecoyPin("5678"))
        assertTrue(app.prefs.verifyPin("1234"))
    }

    @Test
    fun `different primary succeeds without changing decoy`() {
        app.prefs.setDecoyPinHash("1234")
        val activity = launch(decoySetup = false)

        enter(activity, "5678")
        enter(activity, "5678")

        assertTrue(activity.isFinishing)
        assertTrue(app.prefs.verifyPin("5678"))
        assertTrue(app.prefs.verifyDecoyPin("1234"))
    }

    private fun launch(decoySetup: Boolean): PinActivity {
        val intent = Intent().putExtra(
            if (decoySetup) "decoy_setup" else "setup_mode",
            true
        )
        return Robolectric.buildActivity(PinActivity::class.java, intent).setup().get()
    }

    private fun enter(activity: PinActivity, pin: String) {
        pin.forEach { digit ->
            val id = activity.resources.getIdentifier("btn_$digit", "id", activity.packageName)
            activity.findViewById<Button>(id).performClick()
        }
    }

    private fun assertDuplicateRejected(activity: PinActivity, snapshotBefore: String) {
        val error = activity.findViewById<TextView>(R.id.pin_error)
        assertEquals("PINs must be different", error.text.toString())
        assertTrue(error.isFocusable)
        assertEquals(snapshotBefore, app.prefs.exportSnapshot())
        assertFalse(activity.isDestroyed)
        assertFalse(activity.isFinishing)
    }
}
