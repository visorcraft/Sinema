package com.sinema.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * Generates the app's baseline profile by exercising startup and browsing.
 * Run with: ./gradlew :app:generateReleaseBaselineProfile
 *
 * On a fresh emulator the app opens its Setup screen; on a configured one,
 * the Home rows. Both paths cover the classes that dominate cold start
 * (SinemaApp, Prefs, leanback rows, Glide init); the browse steps warm
 * card presenters and dialogs.
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun appStartupAndBrowse() {
        baselineProfileRule.collect(packageName = "com.sinema") {
            pressHome()
            startActivityAndWait()

            // Browse whichever screen appeared (setup menu or home rows).
            repeat(8) {
                device.pressDPadDown()
                device.waitForIdle(1_000)
            }
            repeat(4) {
                device.pressDPadUp()
                device.waitForIdle(500)
            }
            device.pressDPadLeft()
            device.waitForIdle(500)
            device.pressDPadRight()
            device.waitForIdle(500)

            // Open something (setup option, shortcut card, or scene card),
            // then come back.
            device.pressEnter()
            device.waitForIdle(2_000)
            device.pressBack()
            device.waitForIdle(1_000)
        }
    }
}
