package com.sinema.ui

import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.VerticalGridPresenter
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityItem

class LibraryActivity : FragmentActivity() {
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
        title = "Library"
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.sinema_logo)
        val presenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false)
        presenter.numberOfColumns = 3
        setGridPresenter(presenter)
        val shortcuts = SinemaApp.instance.prefs.homeLayout.visibleLibraryShortcuts().map { it.label }
        val items = ArrayObjectAdapter(SettingsPresenter())
        shortcuts.forEach(items::add)
        adapter = items
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                "Browse Folders" -> startActivity(Intent(requireContext(), BrowseFoldersActivity::class.java))
                "Tags" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.TAG))
                "Performers" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.PERFORMER))
                "Studios" -> startActivity(EntityGridActivity.intent(requireContext(), EntityItem.Kind.STUDIO))
                "Markers" -> startActivity(Intent(requireContext(), MarkerBrowserActivity::class.java))
                "Stats" -> startActivity(Intent(requireContext(), StatsActivity::class.java))
                "Saved Filters" -> startActivity(Intent(requireContext(), SavedFiltersActivity::class.java))
            }
        }
        if (shortcuts.isEmpty()) title = "Library (no shortcuts enabled)"
    }
}
