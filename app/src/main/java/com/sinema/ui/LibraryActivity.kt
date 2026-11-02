package com.sinema.ui

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityItem

class LibraryActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, LibraryFragment())
                .commit()
        }
    }
}

class LibraryFragment : VerticalGridSupportFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(com.sinema.R.string.library_title)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.sinema_logo)
        val presenter = VerticalGridPresenter(TvMotion.focusZoom(), false)
        presenter.numberOfColumns = 3
        setGridPresenter(presenter)
        val shortcuts = SinemaApp.instance.prefs.homeLayout.visibleLibraryShortcuts().map { it.label }
        val items = ArrayObjectAdapter(SettingsPresenter())
        shortcuts.forEach(items::add)
        items.add(getString(R.string.library_card_finish_watching))
        items.add(getString(R.string.library_card_untagged))
        items.add(getString(R.string.library_card_server))
        adapter = items
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                "Browse Folders" -> startActivity(Intent(requireContext(), BrowseFoldersActivity::class.java))
                "Groups" -> startActivity(GroupGridActivity.intent(requireContext()))
                "Galleries" -> startActivity(GalleryGridActivity.intent(requireContext()))
                "Tags" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.TAG))
                "Performers" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.PERFORMER))
                "Studios" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.STUDIO))
                "Markers" -> startActivity(Intent(requireContext(), MarkerBrowserActivity::class.java))
                "Stats" -> startActivity(Intent(requireContext(), StatsActivity::class.java))
                getString(R.string.library_card_untagged) ->
                    startActivity(Intent(requireContext(), UntaggedScenesActivity::class.java))
                getString(R.string.library_card_finish_watching) ->
                    startActivity(Intent(requireContext(), FinishWatchingActivity::class.java))
                getString(R.string.library_card_server) ->
                    startActivity(Intent(requireContext(), ServerDashboardActivity::class.java))
                "Saved Filters" -> startActivity(Intent(requireContext(), SavedFiltersActivity::class.java))
            }
        }
        if (shortcuts.isEmpty()) title = getString(R.string.library_no_shortcuts)
    }
}
