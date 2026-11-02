package com.sinema.ui

import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.CaptionRef
import com.sinema.model.EntityItem
import com.sinema.model.MarkerRef
import com.sinema.model.Scene
import com.sinema.model.SceneDetails
import com.sinema.model.StarRating
import com.sinema.util.GlideAuth
import com.sinema.util.loadAuth
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SceneDetailActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private lateinit var detailState: SceneDetailState
    private val scene: Scene get() = detailState.scene

    private lateinit var btnResume: Button
    private lateinit var btnPlay: Button
    private lateinit var btnMarkWatched: Button
    private lateinit var btnFavorite: Button
    private lateinit var btnOCount: Button
    private var btnFolder: Button? = null
    private lateinit var metaView: TextView
    private lateinit var detailsView: TextView
    private lateinit var tagsScroll: View
    private lateinit var tagsRow: LinearLayout
    private lateinit var performersScroll: View
    private lateinit var performersRow: LinearLayout
    private val starButtons: List<Button> by lazy {
        listOf(
            findViewById(R.id.btn_star_1),
            findViewById(R.id.btn_star_2),
            findViewById(R.id.btn_star_3),
            findViewById(R.id.btn_star_4),
            findViewById(R.id.btn_star_5)
        )
    }

    // Captions and markers loaded from Stash after loadDetails completes
    private var captions: List<CaptionRef> = emptyList()
    private var markers: List<MarkerRef> = emptyList()

    // Cache key to skip chip rebuild when metadata is unchanged on re-resume
    private var boundChipsKey: String? = null
    private var relatedSceneId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_detail)

        detailState = SceneDetailState(SceneIntents.sceneFrom(intent))

        val screenshot = findViewById<ImageView>(R.id.detail_screenshot)
        btnResume = findViewById(R.id.btn_resume)
        btnPlay = findViewById(R.id.btn_play)
        btnMarkWatched = findViewById(R.id.btn_mark_watched)
        btnFavorite = findViewById(R.id.btn_favorite)
        btnOCount = findViewById(R.id.btn_o_count)
        btnFolder = findViewById(R.id.btn_folder)
        metaView = findViewById(R.id.detail_meta)
        detailsView = findViewById(R.id.detail_details)
        tagsScroll = findViewById(R.id.detail_tags_scroll)
        tagsRow = findViewById(R.id.detail_tags_row)
        performersScroll = findViewById(R.id.detail_performers_scroll)
        performersRow = findViewById(R.id.detail_performers_row)

        bindSceneText(scene)
        val technicalContainer = findViewById<View>(R.id.technical_container)
        val btnTechnical = findViewById<Button>(R.id.btn_technical)
        btnTechnical.setOnClickListener {
            val show = technicalContainer.visibility != View.VISIBLE
            technicalContainer.visibility = if (show) View.VISIBLE else View.GONE
            btnTechnical.text = if (show) "Hide technical details" else "Technical details"
            if (show) {
                // Reveal the freshly expanded block: it renders below the
                // fold, so scroll it into view once laid out.
                val scroll = findViewById<android.widget.ScrollView>(R.id.detail_scroll)
                technicalContainer.post { scroll.smoothScrollTo(0, technicalContainer.bottom) }
            }
        }

        // Load screenshot — suppressed in discreet mode; the scrimmed hero
        // keeps the layout while showing no explicit artwork.
        if (SinemaApp.instance.prefs.discreetMode) {
            screenshot.visibility = View.INVISIBLE
        } else {
            val url = app.api.getScreenshotUrl(scene.id)
            screenshot.loadAuth(app.api, url)
        }

        // Initial resume button state — hidden until loadDetails responds
        btnResume.visibility = View.GONE

        // Play from start
        btnPlay.setOnClickListener { launchPlayback(0L) }

        // Star rating: tapping star N sets rating100 = N*20; Clear removes
        // the rating (and with it favorite status, which is rating > 0).
        starButtons.forEachIndexed { index, button ->
            button.setOnClickListener { setRating(index + 1) }
        }
        findViewById<Button>(R.id.btn_rating_clear).setOnClickListener { setRating(0) }
        bindStars()

        // Mark Watched / Unwatched toggle
        Log.d("Sinema", "SceneDetail: id=${scene.id} playCount=${scene.playCount} isWatched=${scene.isWatched} rating100=${scene.rating100}")
        bindWatchedButton()
        btnMarkWatched.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val wasWatched = scene.isWatched
                    if (wasWatched) {
                        Log.d("Sinema", "Mark Unwatched: resetting play count for scene ${scene.id}")
                        app.api.resetPlayCount(scene.id)
                    } else {
                        Log.d("Sinema", "Mark Watched: clearing resume_time for scene ${scene.id}")
                        app.api.clearResumeTime(scene.id)
                        Log.d("Sinema", "Mark Watched: incrementing play count for scene ${scene.id}")
                        app.api.incrementPlayCount(scene.id)
                    }
                    detailState.applyWatchedToggle(wasWatched)
                    bindWatchedButton()
                    Toast.makeText(this@SceneDetailActivity,
                        if (wasWatched) "Marked as unwatched" else "Marked as watched",
                        Toast.LENGTH_SHORT).show()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("Sinema", "Mark Watched/Unwatched FAILED for scene ${scene.id}", e)
                    Toast.makeText(this@SceneDetailActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // One-tap favorite: same rule as grids and cards — a rating marks a
        // favorite, so unfavoriting clears the rating (★★★★★ by default).
        bindFavoriteButton()
        btnFavorite.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val wasFavorite = scene.isFavorite
                    if (wasFavorite) {
                        app.api.setSceneRating(scene.id, null)
                        detailState.applyRating(0)
                        bindStars()
                        bindMeta(scene)
                        bindFavoriteButton()
                        Toast.makeText(this@SceneDetailActivity,
                            "Rating cleared — removed from favorites", Toast.LENGTH_SHORT).show()
                    } else {
                        app.api.setSceneRating(scene.id, 100)
                        detailState.applyRating(5)
                        bindStars()
                        bindMeta(scene)
                        bindFavoriteButton()
                        Toast.makeText(this@SceneDetailActivity,
                            "Added to favorites (★★★★★)", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("Sinema", "Favorite toggle failed for ${scene.id}", e)
                    Toast.makeText(this@SceneDetailActivity,
                        "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Synopsis: tap to expand/collapse long text.
        detailsView.setOnClickListener {
            detailsView.maxLines = if (detailsView.maxLines == 3) Int.MAX_VALUE else 3
        }

        // O counter: one tap = one O, mirrors Stash's sceneIncrementO
        btnOCount.setOnClickListener {
            lifecycleScope.launch {
                try {
                    app.api.sceneIncrementO(scene.id)
                    detailState.applyOIncrement()
                    bindMeta(scene)
                    Toast.makeText(this@SceneDetailActivity,
                        "O × ${scene.oCounter}", Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("Sinema", "O count failed for ${scene.id}", e)
                    Toast.makeText(this@SceneDetailActivity,
                        "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Browse Folder
        btnFolder?.setOnClickListener {
            val intent = Intent(this, FolderBrowseActivity::class.java)
            intent.putExtra("path", scene.folder)
            startActivity(intent)
        }

        // Initial focus
        btnPlay.requestFocus()

        // The full-bleed hero lives above the scroll viewport once the user
        // scrolls down; D-pad Up from the first action row returns to it.
        val scrollView = findViewById<android.widget.ScrollView>(R.id.detail_scroll)
        val topRowKeyUp = { _: View, keyCode: Int, event: android.view.KeyEvent ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
            ) {
                scrollView.smoothScrollTo(0, 0)
                true
            } else false
        }
        btnPlay.setOnKeyListener(topRowKeyUp)
        btnResume.setOnKeyListener(topRowKeyUp)
    }

    override fun onResume() {
        super.onResume()
        loadDetails()
    }

    private fun loadDetails() {
        val generation = detailState.startRefresh()
        lifecycleScope.launch {
            try {
                val details = app.api.findSceneFull(scene.id) ?: return@launch
                if (!detailState.applyRefresh(generation, details.scene)) return@launch
                captions = details.scene.captions
                markers = details.markers
                bindSceneText(scene)
                bindWatchedButton()
                bindStars()
                bindFavoriteButton()
                bindResume(details)
                bindMeta(details.scene)
                bindChips(details.scene)
                bindChapterStrip()
                bindGalleryStrip(details.scene.galleries)
                if (relatedSceneId != details.scene.id) {
                    relatedSceneId = details.scene.id
                    loadRelatedScenes(details.scene)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Failed to load scene details for ${scene.id}", e)
            }
        }
    }

    private fun bindWatchedButton() {
        // Short label: the pill row is narrow on 1080p panels.
        btnMarkWatched.text = WatchedToggle.shortButtonLabel(scene.isWatched)
    }

    private fun bindFavoriteButton() {
        btnFavorite.text = if (scene.isFavorite) "♥ Favorited" else "♡ Favorite"
    }

    /** Render ☆/★ states for the 1-5 star picker from the current rating. */
    private fun bindStars() {
        val stars = scene.starCount
        starButtons.forEachIndexed { index, button ->
            button.text = if (index < stars) "★" else "☆"
        }
    }

    private fun setRating(stars: Int) {
        lifecycleScope.launch {
            try {
                app.api.setSceneRating(scene.id, StarRating.toRating100(stars))
                detailState.applyRating(stars)
                bindStars()
                bindFavoriteButton()
                bindMeta(scene)
                Toast.makeText(
                    this@SceneDetailActivity,
                    if (stars > 0) "Rated ${StarRating.glyphs(stars)}" else "Rating cleared",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Failed to set rating for ${scene.id}", e)
                Toast.makeText(this@SceneDetailActivity,
                    "Failed to set rating: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun bindSceneText(s: Scene) {
        findViewById<TextView>(R.id.detail_title).text = s.displayTitle
        findViewById<TextView>(R.id.detail_subtitle).text =
            "${s.formatDuration()}  •  ${s.width}x${s.height}"
        findViewById<TextView>(R.id.detail_filename).text = s.filename
        findViewById<TextView>(R.id.detail_path).text = s.path
        findViewById<TextView>(R.id.detail_technical).text =
            "${s.formatDuration()}  •  ${s.formatSize()}  •  ${s.width}x${s.height}"
    }

    private fun bindResume(details: SceneDetails) {
        if (details.resumeTime > 5.0) {
            val resumeMs = (details.resumeTime * 1000).toLong()
            btnResume.visibility = View.VISIBLE
            btnResume.text = "▶ Resume ${formatMs(resumeMs)}"
            btnResume.setOnClickListener { launchPlayback(resumeMs) }
        } else {
            btnResume.visibility = View.GONE
        }
    }

    private fun bindMeta(s: Scene) {
        val metaParts = buildList {
            s.date?.takeIf { it.isNotBlank() }?.let { add(it) }
            s.studio?.let { add(it.name) }
            s.rating100?.takeIf { it > 0 }?.let { add("★ ${"%.1f".format(it / 20.0)}") }
            if (s.oCounter > 0) add("O × ${s.oCounter}")
            if (markers.isNotEmpty()) add("${markers.size} chapters")
        }
        if (metaParts.isNotEmpty()) {
            metaView.text = metaParts.joinToString("  •  ")
            metaView.visibility = View.VISIBLE
        } else {
            metaView.visibility = View.GONE
        }

        // Synopsis from the scene's Stash details field
        if (s.details.isNullOrBlank()) {
            detailsView.visibility = View.GONE
        } else {
            detailsView.text = s.details
            detailsView.visibility = View.VISIBLE
        }
    }

    private fun bindChips(s: Scene) {
        val newKey = buildString {
            append(s.studio?.id ?: "")
            append("|")
            s.tags.forEach { append(it.id); append(",") }
            append("|")
            s.performers.forEach { append(it.id); append(",") }
        }
        if (newKey == boundChipsKey) return
        boundChipsKey = newKey

        tagsRow.removeAllViews()
        performersRow.removeAllViews()

        // Studio chip at start of tags row
        s.studio?.let { studio ->
            addChip(tagsRow, "🎬 ${studio.name}") {
                startActivity(EntityScenesActivity.intent(
                    this@SceneDetailActivity,
                    EntityItem(studio.id, studio.name, 0, null, EntityItem.Kind.STUDIO)
                ))
            }
        }

        // Tag chips
        for (tag in s.tags) {
            addChip(tagsRow, "#${tag.name}") {
                startActivity(EntityScenesActivity.intent(
                    this@SceneDetailActivity,
                    EntityItem(tag.id, tag.name, 0, null, EntityItem.Kind.TAG)
                ))
            }
        }

        // Performer chips
        for (performer in s.performers) {
            addChip(performersRow, performer.name) {
                startActivity(EntityScenesActivity.intent(
                    this@SceneDetailActivity,
                    EntityItem(performer.id, performer.name, 0, null, EntityItem.Kind.PERFORMER)
                ))
            }
        }

        tagsScroll.visibility = if (tagsRow.childCount > 0) View.VISIBLE else View.GONE
        performersScroll.visibility = if (performersRow.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun addChip(row: LinearLayout, label: String, onClick: () -> Unit) {
        val density = resources.displayMetrics.density
        val chip = tvChip(label, onClick)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = (8 * density).toInt()
        row.addView(chip, params)
    }

    private fun launchPlayback(resumeMs: Long) {
        val intent = SceneIntents.playback(this, scene.id, resumeMs, scene.width, scene.height).apply {
            SceneIntents.putCaptions(this, captions)
            SceneIntents.putMarkers(this, markers)
        }
        startActivity(intent)
    }

    /** Chapters strip: marker frame thumbnails, click jumps into playback. */
    private fun bindChapterStrip() {
        val row = findViewById<LinearLayout>(R.id.chapters_row)
        val scroll = findViewById<View>(R.id.chapters_scroll)
        val label = findViewById<TextView>(R.id.chapters_label)
        row.removeAllViews()
        if (markers.isEmpty()) {
            scroll.visibility = View.GONE
            label.visibility = View.GONE
            return
        }
        label.visibility = View.VISIBLE
        scroll.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        for (marker in markers) {
            val card = LayoutInflater.from(this).inflate(R.layout.item_chapter_card, row, false)
            val title = card.findViewById<TextView>(R.id.chapter_card_title)
            title.text = marker.title.ifBlank { marker.primaryTag }
            card.findViewById<TextView>(R.id.chapter_card_time).text =
                com.sinema.util.TimeFormat.formatSeconds(marker.seconds)
            val image = card.findViewById<ImageView>(R.id.chapter_card_image)
            if (SinemaApp.instance.prefs.discreetMode) {
                image.visibility = View.INVISIBLE
            } else {
                Glide.with(this)
                    .load(GlideAuth.url(app.api, app.api.getMarkerScreenshotUrl(scene.id, marker.seconds)))
                    .centerCrop()
                    .into(image)
            }
            card.isFocusable = true
            card.setOnClickListener { launchPlayback((marker.seconds * 1000).toLong()) }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = (12 * density).toInt()
            card.layoutParams = params
            row.addView(card)
        }
    }

    /** Galleries strip: chips opening the gallery image viewer. */
    private fun bindGalleryStrip(galleries: List<com.sinema.model.GalleryRef>) {
        val row = findViewById<LinearLayout>(R.id.galleries_row)
        val scroll = findViewById<View>(R.id.galleries_scroll)
        val label = findViewById<TextView>(R.id.galleries_label)
        row.removeAllViews()
        if (galleries.isEmpty()) {
            scroll.visibility = View.GONE
            label.visibility = View.GONE
            return
        }
        label.visibility = View.VISIBLE
        scroll.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        for (gallery in galleries) {
            val chip = tvChip("🖼 ${gallery.title.ifBlank { "Gallery" }}") {
                startActivity(
                    GalleryImagesActivity.intent(
                        this@SceneDetailActivity,
                        com.sinema.model.GalleryItem(
                            id = gallery.id, title = gallery.title, imageCount = 0
                        )
                    )
                )
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = (8 * density).toInt()
            row.addView(chip, params)
        }
    }

    private fun loadRelatedScenes(source: Scene) {
        val relatedRow = findViewById<LinearLayout>(R.id.related_row)
        val relatedScroll = findViewById<View>(R.id.related_scroll)
        val relatedLabel = findViewById<TextView>(R.id.related_label)
        relatedRow.removeAllViews()
        relatedScroll.visibility = View.GONE
        relatedLabel.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val related = app.api.findRelatedScenes(source).take(6)
                val (label, scenes) = if (related.isNotEmpty()) {
                    "More Like This" to related
                } else {
                    val (_, folderScenes) = app.api.findScenesInFolderDirect(source.folder, 1, 20)
                    "More from this folder" to folderScenes.filter { it.id != source.id }.shuffled().take(3)
                }
                if (scenes.isEmpty() || relatedSceneId != source.id) return@launch

                relatedLabel.text = label
                relatedLabel.visibility = View.VISIBLE
                relatedRow.visibility = View.VISIBLE
                relatedScroll.visibility = View.VISIBLE

                for (s in scenes) {
                    val card = LayoutInflater.from(this@SceneDetailActivity)
                        .inflate(R.layout.view_card, relatedRow, false)

                    val img = card.findViewById<ImageView>(R.id.card_image)
                    val title = card.findViewById<TextView>(R.id.card_title)
                    val content = card.findViewById<TextView>(R.id.card_content)

                    title.text = s.displayTitle
                    content.text = s.formatDuration()

                    val url = app.api.getScreenshotUrl(s.id)
                    if (SinemaApp.instance.prefs.discreetMode) {
                        img.visibility = View.INVISIBLE
                    } else {
                        Glide.with(this@SceneDetailActivity)
                            .load(GlideAuth.url(app.api, url))
                            .centerCrop()
                            .into(img)
                    }

                    card.isFocusable = true
                    card.setOnClickListener {
                        startActivity(SceneIntents.detail(this@SceneDetailActivity, s))
                    }

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.marginEnd = (16 * resources.displayMetrics.density).toInt()
                    card.layoutParams = params
                    relatedRow.addView(card)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Sinema", "Failed to load related scenes for ${source.id}", e)
            }
        }
    }

    private fun formatMs(ms: Long): String = com.sinema.util.TimeFormat.formatMs(ms)
}
