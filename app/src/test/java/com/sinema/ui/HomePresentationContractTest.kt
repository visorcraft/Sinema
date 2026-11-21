package com.sinema.ui

import android.graphics.drawable.StateListDrawable
import android.widget.FrameLayout
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class HomePresentationContractTest {
    @Test
    fun `home uses scoped scrim and command backgrounds`() {
        val hero = read("src/main/res/layout/view_hero_card.xml")
        val command = read("src/main/res/layout/view_command_card.xml")
        assertTrue(hero.contains("@drawable/home_hero_scrim"))
        assertTrue(hero.contains("android:foreground=\"@drawable/home_hero_focus_ring\""))
        assertTrue(hero.contains("android:textSize=\"24sp\""))
        assertTrue(command.contains("@drawable/home_command_background"))
    }

    @Test
    fun `hero scrim is transparent at top and opaque at bottom`() {
        val gradient = xml("src/main/res/drawable/home_hero_scrim.xml")
            .getElementsByTagName("gradient").item(0) as Element
        assertEquals("90", gradient.getAttribute("android:angle"))
        assertEquals("#F2000000", gradient.getAttribute("android:startColor"))
        assertEquals("#99000000", gradient.getAttribute("android:centerColor"))
        assertEquals("#00000000", gradient.getAttribute("android:endColor"))
    }

    @Test
    fun `hero focus stroke is an unobscured foreground`() {
        val context = RuntimeEnvironment.getApplication()
        val hero = HeroPresenter(SinemaApi("http://server", "key"))
            .onCreateViewHolder(FrameLayout(context)).view
        val foreground = hero.foreground
        assertNotNull("Hero focus ring must draw above match-parent artwork", foreground)
        assertTrue(foreground is StateListDrawable)
        val unfocused = foreground.current
        assertTrue(hero.requestFocus())
        hero.refreshDrawableState()

        assertTrue(foreground.state.contains(android.R.attr.state_focused))
        assertNotSame("Focused hero must select a visible foreground stroke", unfocused, foreground.current)

        val ring = xml("src/main/res/drawable/home_hero_focus_ring.xml")
        val focusedItem = (0 until ring.documentElement.childNodes.length)
            .map(ring.documentElement.childNodes::item)
            .filterIsInstance<Element>()
            .single { it.getAttribute("android:state_focused") == "true" }
        val stroke = focusedItem.getElementsByTagName("stroke").item(0) as Element
        assertEquals("4dp", stroke.getAttribute("android:width"))
        assertEquals("#2AABE0", stroke.getAttribute("android:color"))
    }

    private fun xml(path: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().parse(Paths.get(path).toFile())

    private fun read(path: String): String = String(Files.readAllBytes(Paths.get(path)))
}
