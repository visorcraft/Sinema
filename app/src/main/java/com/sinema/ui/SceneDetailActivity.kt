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
import com.sinema.model.EntityItem
import com.sinema.model.Scene
import com.sinema.util.GlideAuth
import com.sinema.util.SceneIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SceneDetailActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private lateinit var scene: Scene

    private lateinit var btnResume: Button
    private lateinit var btnPlay: Button
    private lateinit var btnFavorite: Button
    private lateinit var btnMarkWatched: Button
    private var btnFolder: Button? = null
    private lateinit var metaView: TextView
    private lateinit var tagsScroll: View
    private lateinit var tagsRow: LinearLayout
    private lateinit var performersScroll: View
    private lateinit var performersRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_detail)

        scene = SceneIntents.sceneFrom(intent)

        // Bind views
        val screenshot = findViewById<ImageView>(R.id.detail_screenshot)
        val titleView = findViewById<TextView>(R.id.detail_title)
        val subtitleView = findViewById<TextView>(R.id.detail_subtitle)
        val pathView = findViewById<TextView>(R.id.detail_path)
        btnResume = findViewById(R.id.btn_resume)
        btnPlay = findViewById(R.id.btn_play)
        btnFavorite = findViewById(R.id.btn_favorite)
        btnMarkWatched = findViewById(R.id.btn_mark_watched)
        btnFolder = findViewById(R.id.btn_folder)
        metaView = findViewById(R.id.detail_meta)
        tagsScroll = findViewById(R.id.detail_tags_scroll)
        tagsRow = findViewById(R.id.detail_tags_row)
        performersScroll = findViewById(R.id.detail_performers_scroll)
        performersRow = findViewById(R.id.detail_performers_row)

        // Set static info
        titleView.text = scene.filename
        subtitleView.text = "${scene.formatDuration()} • ${scene.formatSize()} • ${scene.width}x${scene.height}"
        pathView.text = scene.path

        // Load screenshot
        val url = app.api.getScreenshotUrl(scene.id)
        Glide.with(this)
            .load(GlideAuth.url(app.api, url))
            .centerCrop()
            .into(screenshot)

        // Initial resume button state — hidden until loadDetails responds
        btnResume.visibility = View.GONE

        // Play from start
        btnPlay.setOnClickListener { launchPlayback(0L) }

        // Resume (default click before loadDetails fills in the real position)
        btnResume.setOnClickListener { launchPlayback(0L) }

        // Favorite toggle
        btnFavorite.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val currentRating = app.api.getSceneRating(scene.id)
                    val isFavNow = currentRating != null && currentRating > 0
                    val newFav = !isFavNow
                    app.api.setSceneRating(scene.id, if (newFav) 100 else null)
                    btnFavorite.text = if (newFav) "❤️ Unfavorite" else "🤍 Favorite"
                    Toast.makeText(this@SceneDetailActivity,
                        if (newFav) "Added to favorites" else "Removed from favorites",
                        Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("Sinema", "Failed to toggle favorite", e)
                    Toast.makeText(this@SceneDetailActivity,
                        "Failed to update favorite: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Mark Watched / Unwatched toggle
        Log.d("Sinema", "SceneDetail: id=${scene.id} playCount=${scene.playCount} isWatched=${scene.isWatched} rating100=${scene.rating100}")
        if (scene.isWatched) {
            btnMarkWatched.text = "❌ Mark Unwatched"
        }
        btnMarkWatched.setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (scene.isWatched) {
                        Log.d("Sinema", "Mark Unwatched: resetting play count for scene ${scene.id}")
                        app.api.resetPlayCount(scene.id)
                        runOnUiThread {
                            Toast.makeText(this@SceneDetailActivity, "Marked as unwatched", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Log.d("Sinema", "Mark Watched: clearing resume_time for scene ${scene.id}")
                        app.api.clearResumeTime(scene.id)
                        Log.d("Sinema", "Mark Watched: incrementing play count for scene ${scene.id}")
                        app.api.incrementPlayCount(scene.id)
                        Log.d("Sinema", "Mark Watched: success for scene ${scene.id}")
                        runOnUiThread {
                            Toast.makeText(this@SceneDetailActivity, "Marked as watched", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Sinema", "Mark Watched/Unwatched FAILED for scene ${scene.id}", e)
                    runOnUiThread {
                        Toast.makeText(this@SceneDetailActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Browse Folder
        btnFolder?.setOnClickListener {
            val intent = Intent(this, FolderBrowseActivity::class.java)
            intent.putExtra("path", scene.folder)
            startActivity(intent)
        }

        // Load related scenes from same folder
        loadRelatedScenes()

        // Initial focus
        btnPlay.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        loadDetails()
    }

    private fun loadDetails() {
        lifecycleScope.launch {
            try {
                val details = app.api.findSceneFull(scene.id) ?: return@launch
                val s = details.scene

                // Resume button
                if (details.resumeTime > 5.0) {
                    val resumeMs = (details.resumeTime * 1000).toLong()
                    btnResume.visibility = View.VISIBLE
                    btnResume.text = "▶ Resume from ${formatMs(resumeMs)}"
                    btnResume.setOnClickListener { launchPlayback(resumeMs) }
                } else {
                    btnResume.visibility = View.GONE
                }

                // Favorite initial state
                btnFavorite.text = if (s.isFavorite) "❤️ Unfavorite" else "🤍 Favorite"

                // Meta line: date  •  studio  •  ★ rating
                val metaParts = buildList {
                    s.date?.takeIf { it.isNotBlank() }?.let { add(it) }
                    s.studio?.let { add(it.name) }
                    s.rating100?.let { add("★ $it") }
                }
                if (metaParts.isNotEmpty()) {
                    metaView.text = metaParts.joinToString("  •  ")
                    metaView.visibility = View.VISIBLE
                } else {
                    metaView.visibility = View.GONE
                }

                // Chips — clear before repopulating (onResume runs repeatedly)
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

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Sinema", "Failed to load scene details for ${scene.id}", e)
            }
        }
    }

    private fun addChip(row: LinearLayout, label: String, onClick: () -> Unit) {
        val chip = Button(this).apply {
            text = label
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(chip)
    }

    private fun launchPlayback(resumeMs: Long) {
        val intent = Intent(this, PlaybackActivity::class.java)
        intent.putExtra("scene_id", scene.id)
        intent.putExtra("scene_title", scene.filename)
        intent.putExtra("resume_position_ms", resumeMs)
        startActivity(intent)
    }

    private fun loadRelatedScenes() {
        val relatedRow = findViewById<LinearLayout>(R.id.related_row)
        val relatedLabel = findViewById<TextView>(R.id.related_label)

        lifecycleScope.launch {
            try {
                val (_, scenes) = app.api.findScenesInFolderDirect(scene.folder, 1, 20)
                val others = scenes.filter { it.id != scene.id }.shuffled().take(3)
                if (others.isEmpty()) return@launch

                relatedLabel.visibility = View.VISIBLE
                relatedRow.visibility = View.VISIBLE

                for (s in others) {
                    val card = LayoutInflater.from(this@SceneDetailActivity)
                        .inflate(R.layout.view_card, relatedRow, false)

                    val img = card.findViewById<ImageView>(R.id.card_image)
                    val title = card.findViewById<TextView>(R.id.card_title)
                    val content = card.findViewById<TextView>(R.id.card_content)

                    title.text = s.filename
                    content.text = s.formatDuration()

                    val url = app.api.getScreenshotUrl(s.id)
                    Glide.with(this@SceneDetailActivity)
                        .load(GlideAuth.url(app.api, url))
                        .centerCrop()
                        .into(img)

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
            } catch (_: Exception) {}
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSecs = (ms / 1000).toInt()
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}
