package com.sinema.ui

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import com.sinema.model.FolderItem
import com.sinema.model.InterfaceTextScale
import com.sinema.model.SceneMarkerItem
import org.junit.Assert.assertFalse
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
class CardPresentationContractTest {
    @Test
    fun `cards retain structure and add readable polish`() {
        val card = read("src/main/res/layout/view_card.xml")
        val folder = read("src/main/res/layout/view_folder_card.xml")
        assertTrue(card.startsWith("<?xml") && card.contains("<LinearLayout"))
        assertTrue(card.contains("@+id/card_image"))
        assertTrue(card.contains("@+id/card_title"))
        assertTrue(card.contains("@+id/card_content"))
        assertTrue(card.contains("@drawable/card_polish_badge"))
        assertTrue(card.contains("android:textSize=\"15sp\""))
        assertFalse(folder.contains("android:clipChildren=\"true\""))
    }

    @Test
    fun `scene card contains rendered text at maximum interface scale`() {
        val context = maximumScaleContext()
        val card = CardPresenter(SinemaApi("http://server", "key"))
            .onCreateViewHolder(FrameLayout(context)).view
        card.findViewById<TextView>(R.id.card_title).text = "Scene title"
        card.findViewById<TextView>(R.id.card_content).text = "24 minutes"

        measureAndLayout(card)

        assertTextWithinCard(card)
    }

    @Test
    fun `stat card contains oversized title at maximum interface scale`() {
        val context = maximumScaleContext()
        val presenter = StatCardPresenter()
        val holder = presenter.onCreateViewHolder(FrameLayout(context))
        presenter.onBindViewHolder(holder, StatCard("12,345", "Total scenes"))

        measureAndLayout(holder.view)

        assertTextWithinCard(holder.view)
    }

    @Test
    fun `folder card contains rendered text at maximum interface scale`() {
        val context = maximumScaleContext()
        val presenter = FolderCardPresenter()
        val holder = presenter.onCreateViewHolder(FrameLayout(context))
        presenter.onBindViewHolder(
            holder,
            FolderItem(name = "Maximum-scale folder", fullPath = "/library", isFolder = true, childCount = 12)
        )

        measureAndLayout(holder.view)

        assertTextWithinCard(holder.view)
    }

    @Test
    fun `marker card contains rendered text at maximum interface scale`() {
        val context = maximumScaleContext()
        val presenter = MarkerCardPresenter(SinemaApi("http://server", "key"))
        val holder = presenter.onCreateViewHolder(FrameLayout(context))
        presenter.onBindViewHolder(
            holder,
            SceneMarkerItem(
                id = "m1",
                title = "Maximum-scale marker",
                seconds = 12.0,
                sceneId = "s1",
                sceneTitle = "Marker scene",
                sceneWidth = 1920,
                sceneHeight = 1080,
                primaryTagId = "t1",
                primaryTagName = "Tag"
            )
        )

        measureAndLayout(holder.view)

        assertTextWithinCard(holder.view)
    }

    @Test
    fun `recent search card contains rendered text at maximum interface scale`() {
        val context = maximumScaleContext()
        val presenter = RecentSearchPresenter()
        val holder = presenter.onCreateViewHolder(FrameLayout(context))
        presenter.onBindViewHolder(holder, "Maximum-scale search")

        measureAndLayout(holder.view)

        assertTextWithinCard(holder.view)
    }

    @Test
    fun `scene detail related card contains rendered text at maximum interface scale`() {
        val context = maximumScaleContext()
        val card = LayoutInflater.from(context)
            .inflate(R.layout.view_card, FrameLayout(context), false)
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        card.findViewById<TextView>(R.id.card_title).text = "Related scene"
        card.findViewById<TextView>(R.id.card_content).text = "24 minutes"

        measureAndLayout(card)

        assertTextWithinCard(card)
    }

    private fun maximumScaleContext(): Context {
        val context = RuntimeEnvironment.getApplication()
        val configuration = Configuration(context.resources.configuration).apply {
            fontScale = InterfaceTextScale.EXTRA_LARGE.fontScale
        }
        return context.createConfigurationContext(configuration)
    }

    private fun measureAndLayout(card: View) {
        val params = card.layoutParams
        card.measure(
            if (params.width >= 0) {
                View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY)
            } else {
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            },
            if (params.height >= 0) {
                View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY)
            } else {
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            }
        )
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    }

    private fun assertTextWithinCard(card: View) {
        listOf(R.id.card_title, R.id.card_content).forEach { id ->
            val text = card.findViewById<TextView>(id)
            assertTrue(
                "Rendered ${resourcesName(text)} extends beyond card: bottom=${text.bottom}, " +
                    "cardInterior=${card.height - card.paddingBottom}",
                text.bottom <= card.height - card.paddingBottom
            )
            assertTrue(
                "Rendered ${resourcesName(text)} is clipped vertically",
                text.height >= text.layout.height + text.compoundPaddingTop + text.compoundPaddingBottom
            )
        }
    }

    private fun resourcesName(view: View): String = view.resources.getResourceName(view.id)

    private fun read(path: String): String = String(Files.readAllBytes(Paths.get(path)))
}
