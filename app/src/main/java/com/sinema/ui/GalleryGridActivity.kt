package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.sinema.R
import com.sinema.model.GalleryItem

enum class GallerySortOption(val label: String, val field: String, val direction: String) {
    COUNT_DESC("Image count", "image_count", "DESC"),
    NAME_ASC("Name (A–Z)", "title", "ASC"),
    NAME_DESC("Name (Z–A)", "title", "DESC"),
    UPDATED_DESC("Recently updated", "updated_at", "DESC"),
    DATE_DESC("Release date", "date", "DESC");

    companion object {
        fun fromName(name: String?): GallerySortOption =
            entries.firstOrNull { it.name == name } ?: UPDATED_DESC
    }
}

class GalleryGridActivity : GridActivity() {
    companion object {
        fun intent(context: Context): Intent = Intent(context, GalleryGridActivity::class.java)
    }

    override fun createGridFragment(): SceneGridFragment = GalleryGridFragment()
}

class GalleryGridFragment : SceneGridFragment() {
    override val columns = 4
    override val paged = true
    override val canSearch = true
    override val gridTitle: String
        get() = buildString {
            append("Galleries")
            if (searchQuery.isNotBlank()) append(": $searchQuery")
            append("  •  ${gallerySort.label}")
        }
    override val emptyMessage = "No galleries found"

    private var gallerySort = GallerySortOption.UPDATED_DESC
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        gallerySort = GallerySortOption.fromName(app.prefs.sortFor("gallery_grid"))
        searchQuery = savedInstanceState?.getString("gallery_query").orEmpty()
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("gallery_query", searchQuery)
    }

    override fun showSortDialog() {
        Dialogs.showChoiceDialog(
            requireContext(), "Sort by", GallerySortOption.entries, gallerySort,
            label = { it.label }
        ) { chosen ->
            gallerySort = chosen
            app.prefs.setSortFor("gallery_grid", chosen.name)
            reload()
        }
    }

    override fun showSearchDialog() {
        Dialogs.showTextSearchDialog(
            requireContext(), title = "Search galleries", initialQuery = searchQuery
        ) { query ->
            searchQuery = query
            reload()
        }
    }

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findGalleries(page, perPage, searchQuery.ifBlank { null }, gallerySort.field, gallerySort.direction)

    override fun onItemClicked(item: Any) {
        if (item is GalleryItem) startActivity(GalleryImagesActivity.intent(requireContext(), item))
    }
}
