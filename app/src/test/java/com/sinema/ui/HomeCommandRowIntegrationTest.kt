package com.sinema.ui

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.sinema.SinemaApp
import com.sinema.api.testGraphqlErrorResponse
import com.sinema.api.testScene
import com.sinema.api.testSceneListResponse
import com.sinema.api.withGraphqlServer
import com.sinema.model.HomeLayoutSettings
import com.sinema.model.HomeShortcut
import java.time.Duration
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SinemaApp::class)
class HomeCommandRowIntegrationTest {
    @Before
    fun configureApp() {
        val app = SinemaApp.instance
        app.prefs.serverUrl = "http://127.0.0.1:1"
        app.prefs.apiKey = "test"
        app.prefs.sessionCookie = ""
        app.prefs.authMode = "apikey"
        app.prefs.channelsEnabled = false
        app.prefs.homeLayout = HomeLayoutSettings()
        app.pinVerifiedThisSession = true
        app.decoySession = false
        app.updateCheckedThisSession = true
        app.refreshApi()
    }

    @Test
    fun `pin command row is above browse and log out is first`() {
        val activity = launch(pin = true)
        val rows = homeRows(activity)
        val quick = rows.single { it.headerItem.id == QUICK_ROW_ID }
        val browse = rows.single { it.headerItem.id == BROWSE_ROW_ID }
        assertEquals(listOf("Log Out", "Refresh", "Settings", "About"), items(quick))
        assertTrue(rows.indexOf(quick) < rows.indexOf(browse))
        assertFalse(items(browse).contains("Log Out"))
    }

    @Test
    fun `no pin starts commands with refresh`() {
        val activity = launch(pin = false)
        val quick = homeRows(activity).single { it.headerItem.id == QUICK_ROW_ID }
        assertEquals(listOf("Refresh", "Settings", "About"), items(quick))
    }

    @Test
    fun `quick actions stay below rendered logo with focus clearance`() {
        val activity = launch(pin = true)
        val logo = activity.findViewById<android.widget.ImageView>(com.sinema.R.id.home_logo)
        val quick = itemView(activity, QUICK_ROW_ID, HomeCommandRow.LOG_OUT)
        focusItem(activity, QUICK_ROW_ID, HomeCommandRow.LOG_OUT)
        quick.scaleY = 1.2f
        layoutHome(activity)
        val logoBounds = android.graphics.Rect()
        val quickBounds = android.graphics.Rect()
        logo.getGlobalVisibleRect(logoBounds)
        quick.getGlobalVisibleRect(quickBounds)
        val focusedQuickTop = quickBounds.top + ((quick.height * (quick.scaleY - 1f)) / 2f).toInt()
        assertTrue(mainFragment(activity).requireView().paddingTop >= logo.bottom)
        assertTrue(focusedQuickTop >= logoBounds.bottom)
    }

    @Test
    fun `cold launch uses safe provisional focus before delayed retry response`() {
        val responseGate = CountDownLatch(1)
        withGraphqlServer(
            testGraphqlErrorResponse("delayed"),
            responseGate,
            status = 500,
            configureSinemaApp = true
        ) { _, _ ->
            try {
                val activity = launch(pin = true)
                layoutHome(activity)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                assertFalse(focusedItem(activity) == HomeCommandRow.LOG_OUT)
                assertEquals("Search", focusedItem(activity))
                responseGate.countDown()
                awaitRow(activity, RETRY_ROW_ID)
                awaitFocused(activity, "Retry Connection")
            } finally {
                responseGate.countDown()
            }
        }
    }

    @Test
    fun `setting pin on return adds log out and preserves command focus and edges`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layoutHome(activity)
        shadowOf(Looper.getMainLooper()).idle()
        awaitFocused(activity, "Retry Connection")
        focusItem(activity, QUICK_ROW_ID, HomeCommandRow.SETTINGS)
        SinemaApp.instance.prefs.setPinHash("1234")
        controller.pause().resume()
        layoutHome(activity)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            listOf("Log Out", "Refresh", "Settings", "About"),
            items(row(activity, QUICK_ROW_ID))
        )
        awaitFocused(activity, HomeCommandRow.SETTINGS)
        awaitHome(activity, "Log Out holder after PIN change") {
            runCatching { itemView(activity, QUICK_ROW_ID, HomeCommandRow.LOG_OUT) }.getOrNull() != null
        }
        val logOut = itemView(activity, QUICK_ROW_ID, HomeCommandRow.LOG_OUT)
        assertEquals(logOut.id, logOut.nextFocusLeftId)
    }

    @Test
    fun `removing pin on return removes log out and restores safe refresh edge and focus`() {
        SinemaApp.instance.prefs.setPinHash("1234")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layoutHome(activity)
        shadowOf(Looper.getMainLooper()).idle()
        awaitFocused(activity, "Retry Connection")
        focusItem(activity, QUICK_ROW_ID, HomeCommandRow.LOG_OUT)
        SinemaApp.instance.prefs.removePin()
        controller.pause().resume()
        layoutHome(activity)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            listOf("Refresh", "Settings", "About"),
            items(row(activity, QUICK_ROW_ID))
        )
        val refresh = itemView(activity, QUICK_ROW_ID, HomeCommandRow.REFRESH)
        assertEquals(refresh.id, refresh.nextFocusLeftId)
        awaitFocused(activity, HomeCommandRow.REFRESH)
        assertFalse(focusedItem(activity) == HomeCommandRow.LOG_OUT)
    }

    @Test
    fun `log out clears only session verification and finishes`() {
        val app = SinemaApp.instance
        val activity = launch(pin = true)
        val fragment = mainFragment(activity)
        fragment.onItemViewClickedListener.onItemClicked(null, "Log Out", null, null)
        assertFalse(app.pinVerifiedThisSession)
        assertTrue(app.prefs.hasPinSet())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `browse preserves saved shortcut order and appends tonight mix`() {
        val layout = HomeLayoutSettings(
            shortcutOrder = listOf(HomeShortcut.STATS, HomeShortcut.SEARCH, HomeShortcut.GROUPS)
        )
        SinemaApp.instance.prefs.homeLayout = layout
        val activity = launch(pin = true)
        val browse = homeRows(activity).single { it.headerItem.id == BROWSE_ROW_ID }
        val expected = layout.normalized().visibleShortcuts().map { it.label } +
            activity.getString(com.sinema.R.string.shortcut_tonight_mix)
        assertEquals(expected, items(browse))
    }

    @Test
    fun `initial browse focus waits for leanback layout and never lands on log out`() {
        withGraphqlServer(EMPTY_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true, idleBeforeLayout = true)
            shadowOf(Looper.getMainLooper()).idle()
            layoutHome(activity)
            awaitFocused(activity, "Search")
            assertFalse(focusedItem(activity) == HomeCommandRow.LOG_OUT)
        }
    }

    @Test
    fun `user focus move cancels pending automated focus retry`() {
        withGraphqlServer(EMPTY_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitFocused(activity, "Search")
            val automatedTarget = itemView(activity, QUICK_ROW_ID, HomeCommandRow.REFRESH)
            automatedTarget.isFocusable = false
            dispatchUp(activity)
            val pendingField = MainFragment::class.java.getDeclaredField("focusAttempt").apply {
                isAccessible = true
            }
            assertNotNull(
                "Expected a pending automated focus attempt",
                pendingField.get(mainFragment(activity))
            )
            layoutHome(activity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            focusItem(activity, QUICK_ROW_ID, HomeCommandRow.SETTINGS)
            automatedTarget.isFocusable = true
            repeat(65) {
                layoutHome(activity)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
            assertEquals(HomeCommandRow.SETTINGS, focusedItem(activity))
        }
    }

    @Test
    fun `browse up takes ownership from pending initial focus`() {
        withGraphqlServer(EMPTY_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitFocused(activity, "Search")
            val initialTarget = itemView(activity, BROWSE_ROW_ID, "Search")
            initialTarget.isFocusable = false
            setInitialFocusPending(activity)
            dispatchUp(activity)
            awaitFocused(activity, HomeCommandRow.REFRESH)
            initialTarget.isFocusable = true
            reconcileHome(activity)
            repeat(65) {
                layoutHome(activity)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
            assertEquals(HomeCommandRow.REFRESH, focusedItem(activity))
        }
    }

    @Test
    fun `quick action down takes ownership from pending initial focus`() {
        val activity = launch(pin = true)
        awaitFocused(activity, "Retry Connection")
        val initialTarget = itemView(activity, RETRY_ROW_ID, "Retry Connection")
        initialTarget.isFocusable = false
        setInitialFocusPending(activity)
        focusItem(activity, QUICK_ROW_ID, HomeCommandRow.ABOUT)
        dispatchDown(activity)
        awaitFocused(activity, "Search")
        initialTarget.isFocusable = true
        reconcileHome(activity)
        repeat(65) {
            layoutHome(activity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        }
        assertEquals("Search", focusedItem(activity))
    }

    @Test
    fun `retry receives initial focus after full dashboard failure`() {
        val activity = launch(pin = true)
        awaitRow(activity, RETRY_ROW_ID)
        awaitFocused(activity, "Retry Connection")
        assertFalse(focusedItem(activity) == HomeCommandRow.LOG_OUT)
    }

    @Test
    fun `hero receives initial focus when available`() {
        withGraphqlServer(HERO_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitRow(activity, HERO_ROW_ID)
            awaitFocusedItem(activity) { it is HeroItem }
            assertFalse(focusedItem(activity) == HomeCommandRow.LOG_OUT)
        }
    }

    @Test
    fun `launcher sync keeps fetch time profile after mid load switch`() {
        ServerTransitionTestSupport.reset()
        SinemaApp.instance.prefs.channelsEnabled = true
        SinemaApp.instance.prefs.activeProfileId = "profile-a"
        val responseGate = CountDownLatch(1)
        withGraphqlServer(HERO_RESPONSE, responseGate, configureSinemaApp = true) { _, _ ->
            try {
                val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
                SinemaApp.instance.prefs.activeProfileId = "profile-b"
                responseGate.countDown()
                awaitRow(activity, HERO_ROW_ID)
                shadowOf(Looper.getMainLooper()).idle()
                val syncJob = MainFragment::class.java.getDeclaredField("launcherSyncJob")
                    .apply { isAccessible = true }
                    .get(mainFragment(activity)) as? kotlinx.coroutines.Job
                syncJob?.let { job -> kotlinx.coroutines.runBlocking { job.join() } }
                assertTrue(com.sinema.util.TvChannelsIsolationTest.RecordingProvider.inserts.isEmpty())
            } finally {
                responseGate.countDown()
            }
        }
    }

    @Test
    fun `hero down focuses refresh`() {
        withGraphqlServer(HERO_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitRow(activity, HERO_ROW_ID)
            awaitFocusedItem(activity) { it is HeroItem }
            dispatchDown(activity)
            awaitFocused(activity, HomeCommandRow.REFRESH)
        }
    }

    @Test
    fun `quick action down focuses first visible browse item`() {
        withGraphqlServer(EMPTY_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitFocused(activity, "Search")
            focusItem(activity, QUICK_ROW_ID, HomeCommandRow.ABOUT)
            dispatchDown(activity)
            awaitFocused(activity, "Search")
        }
    }

    @Test
    fun `browse up focuses refresh instead of log out`() {
        withGraphqlServer(EMPTY_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitFocused(activity, "Search")
            dispatchUp(activity)
            awaitFocused(activity, HomeCommandRow.REFRESH)
            assertFalse(focusedItem(activity) == HomeCommandRow.LOG_OUT)
        }
    }

    @Test
    fun `empty browse directional fallback focuses refresh`() {
        withGraphqlServer(EMPTY_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitFocused(activity, "Search")
            focusItem(activity, QUICK_ROW_ID, HomeCommandRow.SETTINGS)
            (row(activity, BROWSE_ROW_ID).adapter as ArrayObjectAdapter).clear()
            dispatchDown(activity)
            awaitFocused(activity, HomeCommandRow.REFRESH)
            assertFalse(focusedItem(activity) == HomeCommandRow.LOG_OUT)
        }
    }

    @Test
    fun `reload restoration requests focus for originating quick action`() {
        withGraphqlServer(EMPTY_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitFocused(activity, "Search")
            focusItem(activity, QUICK_ROW_ID, HomeCommandRow.SETTINGS)
            mainFragment(activity).onItemViewClickedListener.onItemClicked(
                null, HomeCommandRow.REFRESH, null, null
            )
            activity.window.decorView.findFocus()?.clearFocus()
            awaitFocused(activity, HomeCommandRow.SETTINGS)
        }
    }

    @Test
    fun `reload invalid item falls back to refresh rather than log out`() {
        withGraphqlServer(EMPTY_RESPONSE, configureSinemaApp = true) { _, _ ->
            val activity = launch(pin = true)
            awaitFocused(activity, "Search")
            mainFragment(activity).onItemViewClickedListener.onItemClicked(
                null, HomeCommandRow.REFRESH, null, null
            )
            (row(activity, BROWSE_ROW_ID).adapter as ArrayObjectAdapter).clear()
            activity.window.decorView.findFocus()?.clearFocus()
            awaitFocused(activity, HomeCommandRow.REFRESH)
            assertFalse(focusedItem(activity) == HomeCommandRow.LOG_OUT)
        }
    }

    @Test
    fun `hero holder rebind restores down navigation after unbind`() {
        val activity = launch(pin = false)
        var calls = 0
        val presenter = HeroPresenter(SinemaApp.instance.api, onDown = { calls++ })
        val holder = presenter.onCreateViewHolder(FrameLayout(activity))
        val hero = HeroItem(testScene(id = "hero", resumeTimeSec = 12.0), 12_000L)
        presenter.onBindViewHolder(holder, hero)
        presenter.onUnbindViewHolder(holder)
        presenter.onBindViewHolder(holder, hero)
        holder.view.layout(0, 0, 640, 360)
        assertTrue(holder.view.requestFocus())
        assertTrue(holder.view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
        assertEquals(1, calls)
    }

    @Test
    fun `hero click emits confirmed resume intent`() {
        val activity = launch(pin = false)
        val hero = HeroItem(testScene(id = "hero", resumeTimeSec = 12.0), 12_000L)
        mainFragment(activity).onItemViewClickedListener.onItemClicked(null, hero, null, null)
        val intent = shadowOf(activity).nextStartedActivity
        assertTrue(com.sinema.util.SceneIntents.resumeConfirmedFrom(intent))
        assertEquals(12_000L, com.sinema.util.SceneIntents.resumeMsFrom(intent))
    }

    @Test
    fun `browse holder rebind restores up navigation after unbind`() {
        val activity = launch(pin = false)
        var calls = 0
        val presenter = SettingsPresenter(onUp = { calls++ })
        val holder = presenter.onCreateViewHolder(FrameLayout(activity))
        presenter.onBindViewHolder(holder, "Search")
        presenter.onUnbindViewHolder(holder)
        presenter.onBindViewHolder(holder, "Search")
        holder.view.layout(0, 0, 200, 180)
        assertTrue(holder.view.requestFocus())
        assertTrue(holder.view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
        assertEquals(1, calls)
    }

    private fun launch(pin: Boolean, idleBeforeLayout: Boolean = false): MainActivity {
        if (pin) SinemaApp.instance.prefs.setPinHash("1234")
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        if (!idleBeforeLayout) layoutHome(activity)
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun mainFragment(activity: MainActivity): MainFragment =
        activity.supportFragmentManager.findFragmentById(com.sinema.R.id.main_frame) as MainFragment

    private fun homeRows(activity: MainActivity): List<ListRow> {
        val adapter = mainFragment(activity).adapter as ArrayObjectAdapter
        return (0 until adapter.size()).mapNotNull { adapter.get(it) as? ListRow }
    }

    private fun row(activity: MainActivity, id: Long): ListRow =
        homeRows(activity).single { it.headerItem.id == id }

    private fun items(row: ListRow): List<String> {
        val adapter = row.adapter as ArrayObjectAdapter
        return (0 until adapter.size()).map { adapter.get(it) as String }
    }

    private fun layoutHome(activity: MainActivity) {
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        )
        decor.layout(0, 0, 1920, 1080)
    }

    private fun rowHolder(activity: MainActivity, row: ListRow): ListRowPresenter.ViewHolder {
        val fragment = mainFragment(activity)
        val adapter = fragment.adapter as ArrayObjectAdapter
        val rowIndex = (0 until adapter.size()).indexOfFirst { adapter.get(it) === row }
        assertTrue("Row is not attached: ${row.headerItem.id}", rowIndex >= 0)
        val holder = fragment.getRowViewHolder(rowIndex) as? ListRowPresenter.ViewHolder
        assertNotNull("Row holder is not laid out: ${row.headerItem.id}", holder)
        return holder!!
    }

    private fun itemView(activity: MainActivity, rowId: Long, item: Any): View {
        val row = row(activity, rowId)
        val adapter = row.adapter as ArrayObjectAdapter
        val index = (0 until adapter.size()).indexOfFirst { adapter.get(it) == item }
        assertTrue("Item is not present: $item", index >= 0)
        val view = rowHolder(activity, row).getItemViewHolder(index)?.view
        assertNotNull("Item holder is not laid out: $item", view)
        return view!!
    }

    private fun focusItem(activity: MainActivity, rowId: Long, item: Any) {
        val view = itemView(activity, rowId, item)
        assertTrue(view.isShown)
        assertTrue(view.isEnabled)
        assertTrue(view.isFocusable)
        assertTrue(view.requestFocus())
        assertTrue(view.hasFocus())
    }

    private fun focusedItem(activity: MainActivity): Any? {
        val focused = activity.window.decorView.findFocus() ?: return null
        homeRows(activity).forEach { row ->
            val adapter = row.adapter as ArrayObjectAdapter
            val holder = runCatching { rowHolder(activity, row) }.getOrNull() ?: return@forEach
            for (index in 0 until adapter.size()) {
                if (holder.getItemViewHolder(index)?.view === focused) {
                    return adapter.get(index)
                }
            }
        }
        return null
    }

    private fun dispatchDown(activity: MainActivity) {
        val focused = activity.window.decorView.findFocus()
        assertNotNull(focused)
        assertTrue(focused!!.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
    }

    private fun dispatchUp(activity: MainActivity) {
        val focused = activity.window.decorView.findFocus()
        assertNotNull(focused)
        assertTrue(focused!!.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
    }

    private fun awaitRow(activity: MainActivity, id: Long) {
        awaitHome(activity, "row $id; focused=${focusedItem(activity)}") {
            homeRows(activity).any { it.headerItem.id == id }
        }
    }

    private fun awaitFocused(activity: MainActivity, item: String) {
        awaitHome(activity, "focus $item; focused=${focusedItem(activity)}") {
            focusedItem(activity) == item
        }
    }

    private fun awaitFocusedItem(activity: MainActivity, predicate: (Any?) -> Boolean) {
        awaitHome(activity, "focused item predicate; focused=${focusedItem(activity)}") {
            predicate(focusedItem(activity))
        }
    }

    private fun awaitHome(activity: MainActivity, failure: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            if (System.nanoTime() >= deadline) {
                val grid = mainFragment(activity).verticalGridView
                val browseView = runCatching { itemView(activity, BROWSE_ROW_ID, "Search") }.getOrNull()
                throw AssertionError(
                    "Timed out waiting for $failure; window=${activity.hasWindowFocus()}, " +
                        "decorAttached=${activity.window.decorView.isAttachedToWindow}, " +
                        "decorShown=${activity.window.decorView.isShown}, gridChildren=${grid?.childCount}, " +
                        "gridFocus=${grid?.hasFocus()}, browseShown=${browseView?.isShown}, " +
                        "browseFocusable=${browseView?.isFocusable}, focused=${focusedItem(activity)}"
                )
            }
            layoutHome(activity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        }
        layoutHome(activity)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun setInitialFocusPending(activity: MainActivity) {
        val fragment = mainFragment(activity)
        MainFragment::class.java.getDeclaredField("initialFocusApplied").apply {
            isAccessible = true
        }.setBoolean(fragment, false)
        reconcileHome(activity)
        val pending = MainFragment::class.java.getDeclaredField("focusAttempt").apply {
            isAccessible = true
        }.get(fragment)
        assertNotNull("Expected initial focus to be pending", pending)
    }

    private fun reconcileHome(activity: MainActivity) {
        MainFragment::class.java.getDeclaredMethod("reconcileHomeRows").apply {
            isAccessible = true
        }.invoke(mainFragment(activity))
    }

    private companion object {
        const val HERO_ROW_ID = 1L
        const val BROWSE_ROW_ID = 6L
        const val QUICK_ROW_ID = 7L
        const val RETRY_ROW_ID = 14L
        val EMPTY_RESPONSE = testSceneListResponse()
        val HERO_RESPONSE = testSceneListResponse(
            testScene(
                id = "hero",
                title = "Hero",
                durationSec = 100.0,
                playCount = 1,
                rating100 = 100,
                resumeTimeSec = 12.0,
                lastPlayedAt = "2026-01-01T00:00:00Z"
            )
        )
    }
}
