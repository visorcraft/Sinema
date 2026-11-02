package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.GalleryItem
import com.sinema.model.ImageItem
import com.sinema.util.GlideAuth

class GalleryImagesActivity : FragmentActivity() {
    companion object {
        private const val EXTRA_ID = "gallery_id"
        private const val EXTRA_NAME = "gallery_name"
        private const val EXTRA_COUNT = "gallery_count"
        private const val EXTRA_COVER = "gallery_cover"
        private const val EXTRA_DATE = "gallery_date"
        private const val EXTRA_DETAILS = "gallery_details"
        private const val EXTRA_PHOTOGRAPHER = "gallery_photographer"
        private const val EXTRA_STUDIO = "gallery_studio"

        fun intent(context: Context, gallery: GalleryItem): Intent =
            Intent(context, GalleryImagesActivity::class.java)
                .putExtra(EXTRA_ID, gallery.id)
                .putExtra(EXTRA_NAME, gallery.displayTitle)
                .putExtra(EXTRA_COUNT, gallery.imageCount)
                .putExtra(EXTRA_COVER, gallery.coverImageId)
                .putExtra(EXTRA_DATE, gallery.date)
                .putExtra(EXTRA_DETAILS, gallery.details)
                .putExtra(EXTRA_PHOTOGRAPHER, gallery.photographer)
                .putExtra(EXTRA_STUDIO, gallery.studioName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        bindHeader()
        val galleryId = intent.getStringExtra(EXTRA_ID).orEmpty()
        val fragment = supportFragmentManager.findFragmentById(R.id.main_frame) as? GalleryImagesFragment
            ?: GalleryImagesFragment.create(galleryId, intent.getStringExtra(EXTRA_NAME).orEmpty()).also {
                supportFragmentManager.beginTransaction().replace(R.id.main_frame, it).commit()
            }
        bindGridControls(fragment)
        findViewById<Button>(R.id.btn_grid_sort).visibility = View.GONE
        findViewById<Button>(R.id.btn_group_resume).apply {
            visibility = View.VISIBLE
            text = "▶ Slideshow"
            setOnClickListener {
                startActivity(ImageViewActivity.intentForGallery(this@GalleryImagesActivity, galleryId, 0, true))
            }
        }
        // Collapse the gallery header while the grid scrolls; restore at top.
        val frame = findViewById<View>(R.id.main_frame)
        frame.post {
            frame.findRecyclerView()?.let { grid ->
                findViewById<View>(R.id.entity_header)?.let { header ->
                    CollapsingHeader.bind(grid, header)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)

    private fun bindHeader() {
        findViewById<View>(R.id.entity_header).visibility = View.VISIBLE
        findViewById<TextView>(R.id.entity_header_name).text = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val count = intent.getIntExtra(EXTRA_COUNT, 0)
        val metadata = buildList {
            add("$count " + if (count == 1) "image" else "images")
            intent.getStringExtra(EXTRA_DATE)?.takeIf { it.isNotBlank() }?.let(::add)
            intent.getStringExtra(EXTRA_PHOTOGRAPHER)?.takeIf { it.isNotBlank() }?.let(::add)
            intent.getStringExtra(EXTRA_STUDIO)?.takeIf { it.isNotBlank() }?.let(::add)
        }
        findViewById<TextView>(R.id.entity_header_count).text = metadata.joinToString("  •  ")
        val details = intent.getStringExtra(EXTRA_DETAILS).orEmpty()
        findViewById<TextView>(R.id.entity_header_details).apply {
            text = details
            visibility = if (details.isBlank()) View.GONE else View.VISIBLE
        }
        val coverId = intent.getStringExtra(EXTRA_COVER)
        val image = findViewById<ImageView>(R.id.entity_header_image)
        if (coverId.isNullOrBlank()) {
            image.visibility = View.INVISIBLE
        } else {
            val api = SinemaApp.instance.api
            Glide.with(this)
                .load(GlideAuth.url(api, api.getImageThumbnailUrl(coverId)))
                .centerCrop()
                .into(image)
        }
    }
}

class GalleryImagesFragment : SceneGridFragment() {
    companion object {
        fun create(id: String, name: String) = GalleryImagesFragment().apply {
            arguments = Bundle().apply {
                putString("id", id)
                putString("name", name)
            }
        }
    }

    override val columns = 4
    override val paged = true
    override val gridTitle: String get() = requireArguments().getString("name").orEmpty()
    override val emptyMessage = "No images in this gallery"

    override fun createCardPresenter() = FolderCardPresenter()

    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findImagesForGallery(requireArguments().getString("id").orEmpty(), page, perPage)

    override fun onItemClicked(item: Any) {
        if (item !is ImageItem) return
        val images = (0 until gridAdapter.size()).mapNotNull { gridAdapter.get(it) as? ImageItem }
        val index = images.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        startActivity(
            ImageViewActivity.intentForGallery(
                requireContext(), requireArguments().getString("id").orEmpty(), index
            )
        )
    }
}
