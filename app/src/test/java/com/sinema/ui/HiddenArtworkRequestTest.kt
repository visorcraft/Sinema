package com.sinema.ui

import android.widget.FrameLayout
import androidx.leanback.widget.Presenter
import com.sinema.SinemaApp
import com.sinema.api.SinemaApi
import com.sinema.model.EntityItem
import com.sinema.model.FolderItem
import com.sinema.model.GroupItem
import com.sinema.model.SceneMarkerItem
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class HiddenArtworkRequestTest {
    private lateinit var server: HttpServer
    private val requests = AtomicInteger(0)
    private lateinit var serverUrl: String

    @Before
    fun setUp() {
        requests.set(0)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        serverUrl = "http://127.0.0.1:${server.address.port}"
        SinemaApp.instance.prefs.serverUrl = serverUrl
        SinemaApp.instance.prefs.apiKey = "key"
        SinemaApp.instance.prefs.authMode = "apikey"
        SinemaApp.instance.prefs.discreetMode = true
        SinemaApp.instance.refreshApi()
    }

    @Test
    fun `unfocused discreet cards make no artwork requests`() {
        val context = RuntimeEnvironment.getApplication()
        val cardPresenter = CardPresenter(SinemaApi(serverUrl, "key"))
        val folderPresenter = FolderCardPresenter()
        val markerPresenter = MarkerCardPresenter(SinemaApi(serverUrl, "key"))
        val bindings = listOf(
            Triple(cardPresenter, GroupItem("g", "Group", 1, frontImagePath = "$serverUrl/group.jpg"), "group"),
            Triple(cardPresenter, EntityItem("e", "Entity", 1, "$serverUrl/entity.jpg", EntityItem.Kind.TAG), "entity"),
            Triple(folderPresenter, FolderItem("Folder", "/private", true, firstSceneId = "scene"), "folder"),
            Triple(
                markerPresenter,
                SceneMarkerItem("m", "Marker", 12.0, "scene", "Scene", 1920, 1080, "t", "Tag"),
                "marker"
            )
        )

        bindings.forEach { (presenter, item, _) ->
            val holder = presenter.onCreateViewHolder(FrameLayout(context))
            presenter.onBindViewHolder(holder, item)
            holder.view.clearFocus()
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(100)

        assertEquals(0, requests.get())
        server.stop(0)
    }
}
