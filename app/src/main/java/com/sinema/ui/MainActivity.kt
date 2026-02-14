package com.sinema.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private var waitingForPin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Wipe saved fragment state to prevent zombie fragment restoration
        savedInstanceState?.remove("android:support_fragments")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Check if API key is configured - if not, go to setup
        if (!app.prefs.isConfigured) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        
        // Check if PIN is required and not verified
        if (app.prefs.hasPinSet() && !app.pinVerifiedThisSession) {
            waitingForPin = true
            startActivity(Intent(this, PinActivity::class.java))
            return
        }
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frame, MainFragment())
            .commitNowAllowingStateLoss()
    }
    
    override fun onResume() {
        super.onResume()
        if (waitingForPin && app.pinVerifiedThisSession) {
            waitingForPin = false
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, MainFragment())
                .commitNowAllowingStateLoss()
        } else if (waitingForPin && !app.pinVerifiedThisSession) {
            // PIN was cancelled/failed — PinActivity calls finishAffinity on back
        }
    }
}

class MainFragment : RowsSupportFragment() {
    private val app get() = SinemaApp.instance
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private var isLoading = false

    private var logoView: android.widget.ImageView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0xFF1B1B1B.toInt())
        // Overscan-safe padding so content isn't clipped at screen edges
        val density = resources.displayMetrics.density
        val hPad = (48 * density).toInt()
        val vPad = (27 * density).toInt()
        val topPad = (80 * density).toInt()   // room for logo above content
        view.setPadding(hPad, topPad, hPad, vPad)
        (view as? ViewGroup)?.clipToPadding = false
        // Also set padding on the VerticalGridView (actual row container)
        verticalGridView?.let { grid ->
            grid.setPadding(hPad, topPad, hPad, vPad)
            grid.clipToPadding = false
        }

        // Add Sinema logo to activity's FrameLayout (not the fragment's RecyclerView!)
        val logo = android.widget.ImageView(requireContext()).apply {
            setImageResource(R.drawable.sinema_logo)
            adjustViewBounds = true
            val logoW = (180 * density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(logoW, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = hPad
                topMargin = (20 * density).toInt()
            }
        }
        // Add to the activity's root FrameLayout, above the fragment
        (requireActivity().findViewById<View>(R.id.main_frame)?.parent as? ViewGroup)?.addView(logo)
        logoView = logo

        // Scroll the logo away with content
        verticalGridView?.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            private var totalScrolled = 0
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                totalScrolled += dy
                logoView?.translationY = -totalScrolled.toFloat()
            }
        })

        val lrp = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        lrp.shadowEnabled = false
        rowsAdapter = ArrayObjectAdapter(lrp)
        adapter = rowsAdapter

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Scene -> {
                    val intent = Intent(requireContext(), SceneDetailActivity::class.java)
                    intent.putExtra("scene_id", item.id)
                    intent.putExtra("scene_path", item.path)
                    intent.putExtra("scene_duration", item.duration)
                    intent.putExtra("scene_size", item.size)
                    intent.putExtra("scene_width", item.width)
                    intent.putExtra("scene_height", item.height)
                    intent.putExtra("scene_play_count", item.playCount)
                    intent.putExtra("scene_rating100", item.rating100 ?: -1)
                    intent.putExtra("scene_title", item.title)
                    startActivity(intent)
                }
                is String -> {
                    when (item) {
                        "Settings" -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
                        "Search" -> startActivity(Intent(requireContext(), SearchActivity::class.java))
                        "Favorites" -> startActivity(Intent(requireContext(), FavoritesActivity::class.java))
                        "Browse Folders" -> startActivity(Intent(requireContext(), BrowseFoldersActivity::class.java))
                        "Log Out" -> {
                            app.pinVerifiedThisSession = false
                            requireActivity().finishAffinity()
                        }
                        "Refresh" -> {
                            loadContent()
                            Toast.makeText(requireContext(), "Refreshing...", Toast.LENGTH_SHORT).show()
                        }
                        "About" -> {
                            android.app.AlertDialog.Builder(requireContext())
                                .setTitle("Sinema")
                                .setMessage("Version ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}\n\nAn Android TV client for Stash.\n\nCreated by VisorCraft\nhttps://github.com/visorcraft/Sinema")
                                .setPositiveButton("OK", null)
                                .setNeutralButton("Check for Updates") { _, _ ->
                                    lifecycleScope.launch {
                                        val update = com.sinema.util.UpdateChecker.checkForUpdate(requireContext())
                                        if (update != null) {
                                            com.sinema.util.UpdateChecker.promptUpdate(requireContext(), update)
                                        } else {
                                            Toast.makeText(requireContext(), "You're up to date!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .show()
                        }
                    }
                }
                // FolderItem clicks handled in BrowseFoldersActivity
            }
        }
    }

    // hasLoaded removed — always refresh on resume so Mark Watched etc. reflect immediately

    override fun onResume() {
        super.onResume()
        loadContent()
    }

    fun forceReload() {
        loadContent()
    }

    private fun loadContent() {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                Log.d("Sinema", "loadContent starting")
                rowsAdapter.clear()

                // 1. Top row: Search | Favorites | Browse Folders
                val topAdapter = ArrayObjectAdapter(SettingsPresenter())
                topAdapter.add("Search")
                topAdapter.add("Favorites")
                topAdapter.add("Browse Folders")
                if (app.prefs.hasPinSet()) {
                    topAdapter.add("Log Out")
                }
                rowsAdapter.add(ListRow(HeaderItem(""), topAdapter))

                // 2. Continue Playing (from Stash resume_time)
                val continuePairs = app.api.findContinuePlaying()
                if (continuePairs.isNotEmpty()) {
                    val continueAdapter = ArrayObjectAdapter(CardPresenter(app.api))
                    continuePairs.forEach { (scene, _) -> continueAdapter.add(scene) }
                    rowsAdapter.add(ListRow(HeaderItem("Continue Playing"), continueAdapter))
                }

                // 3. Recently Played (from Stash play history)
                val recentlyPlayed = app.api.findRecentlyPlayed(25)
                if (recentlyPlayed.isNotEmpty()) {
                    val watchedAdapter = ArrayObjectAdapter(CardPresenter(app.api))
                    recentlyPlayed.forEach { watchedAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem("Recently Played"), watchedAdapter))
                }

                // 3. Recently Added
                val recentScenes = app.api.findRecentScenes(25)
                if (recentScenes.isNotEmpty()) {
                    val recentAdapter = ArrayObjectAdapter(CardPresenter(app.api))
                    recentScenes.forEach { recentAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem("Recently Added"), recentAdapter))
                }

                // 4. Favorites from Stash (rated scenes)
                val favScenes = app.api.findFavoriteScenes()
                if (favScenes.isNotEmpty()) {
                    val favAdapter = ArrayObjectAdapter(CardPresenter(app.api))
                    favScenes.take(20).forEach { favAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem("Favorites"), favAdapter))
                }

                Log.d("Sinema", "Rows loaded: ${rowsAdapter.size()}")

            } catch (e: Exception) {
                Log.e("Sinema", "loadContent failed", e)
                val message = e.message ?: "Unknown error"
                if (message.contains("401")) {
                    Toast.makeText(requireContext(), "Authentication failed. Please sign in again.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(requireContext(), SetupActivity::class.java))
                    requireActivity().finish()
                    return@launch
                } else {
                    Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_LONG).show()
                }
            } finally {
                // Always add bottom settings row (even if content load fails)
                val settingsAdapter = ArrayObjectAdapter(SettingsPresenter())
                settingsAdapter.add("Refresh")
                settingsAdapter.add("Settings")
                settingsAdapter.add("About")
                rowsAdapter.add(ListRow(HeaderItem(""), settingsAdapter))

                isLoading = false

                // Check for updates (once per session)
                if (!app.updateCheckedThisSession) {
                    app.updateCheckedThisSession = true
                    val update = com.sinema.util.UpdateChecker.checkForUpdate(requireContext())
                    if (update != null) {
                        com.sinema.util.UpdateChecker.promptUpdate(requireContext(), update)
                    }
                }
            }
        }
    }
}
