package com.sinema.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SceneIntentsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `ordinary play does not confirm resume`() {
        assertFalse(SceneIntents.resumeConfirmedFrom(SceneIntents.playback(context, "scene", 45_000L)))
    }

    @Test
    fun `confirmed resume survives intent transport`() {
        val intent = SceneIntents.playback(context, "scene", 45_000L, resumeConfirmed = true)
        assertTrue(SceneIntents.resumeConfirmedFrom(intent))
    }
}
