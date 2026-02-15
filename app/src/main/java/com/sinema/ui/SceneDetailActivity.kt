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
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.Scene
import kotlinx.coroutines.launch

class SceneDetailActivity : FragmentActivity() {
    private val app get() = SinemaApp.instance
    private lateinit var scene: Scene

    private lateinit var btnResume: Button
    private lateinit var btnPlay: Button
    private lateinit var btnFavorite: Button
    private lateinit var btnMarkWatched: Button
    private var btnFolder: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_detail)

        scene = Scene(
            id = intent.getStringExtra("scene_id") ?: "",
            title = intent.getStringExtra("scene_title") ?: "",
            path = intent.getStringExtra("scene_path") ?: "",
            duration = intent.getDoubleExtra("scene_duration", 0.0),
            size = intent.getLongExtra("scene_size", 0L),
            width = intent.getIntExtra("scene_width", 0),
            height = intent.getIntExtra("scene_height", 0),
            playCount = intent.getIntExtra("scene_play_count", 0),
            rating100 = intent.getIntExtra("scene_rating100", -1).let { if (it == -1) null else it }
        )

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

        // Set info
        titleView.text = scene.filename
        subtitleView.text = "${scene.formatDuration()} • ${scene.formatSize()} • ${scene.width}x${scene.height}"
        pathView.text = scene.path

        // Load screenshot
        val url = app.api.getScreenshotUrl(scene.id)
        val glideUrl = GlideUrl(url, LazyHeaders.Builder()
            .addHeader("ApiKey", app.prefs.apiKey)
            .build())
        Glide.with(this)
            .load(glideUrl)
            .centerCrop()
            .into(screenshot)

        // Resume button — fetch from Stash
        updateResumeButton()
        lifecycleScope.launch {
            try {
                val continuePairs = app.api.findContinuePlaying()
                val match = continuePairs.find { it.first.id == scene.id }
                if (match != null && match.second > 5.0) {
                    val resumeMs = (match.second * 1000).toLong()
                    runOnUiThread {
                        btnResume.visibility = View.VISIBLE
                        btnResume.text = "▶ Resume from ${formatMs(resumeMs)}"
                        btnResume.setOnClickListener {
                            val intent = Intent(this@SceneDetailActivity, PlaybackActivity::class.java)
                            intent.putExtra("scene_id", scene.id)
                            intent.putExtra("scene_title", scene.filename)
                            intent.putExtra("resume_position_ms", resumeMs)
                            startActivity(intent)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Play (from start — Stash will clear resume_time when saveActivity sends 0)
        btnPlay.setOnClickListener {
            val intent = Intent(this, PlaybackActivity::class.java)
            intent.putExtra("scene_id", scene.id)
            intent.putExtra("scene_title", scene.filename)
            intent.putExtra("resume_position_ms", 0L)
            startActivity(intent)
        }

        // Resume (default — will be updated with Stash data above)
        btnResume.setOnClickListener {
            val intent = Intent(this, PlaybackActivity::class.java)
            intent.putExtra("scene_id", scene.id)
            intent.putExtra("scene_title", scene.filename)
            startActivity(intent)
        }

        // Favorite
        lifecycleScope.launch {
            val rating = app.api.getSceneRating(scene.id)
            val isFav = rating != null && rating > 0
            btnFavorite.text = if (isFav) "❤️ Unfavorite" else "🤍 Favorite"
        }

        btnFavorite.setOnClickListener {
            lifecycleScope.launch {
                val currentRating = app.api.getSceneRating(scene.id)
                val isFavNow = currentRating != null && currentRating > 0
                val newFav = !isFavNow
                app.api.setSceneRating(scene.id, if (newFav) 100 else null)
                btnFavorite.text = if (newFav) "❤️ Unfavorite" else "🤍 Favorite"
                Toast.makeText(this@SceneDetailActivity,
                    if (newFav) "Added to favorites" else "Removed from favorites",
                    Toast.LENGTH_SHORT).show()
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

        // Set initial focus to resume or play
        if (btnResume.visibility == View.VISIBLE) {
            btnResume.requestFocus()
        } else {
            btnPlay.requestFocus()
        }
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
                    val glideUrl = GlideUrl(url, LazyHeaders.Builder()
                        .addHeader("ApiKey", app.prefs.apiKey)
                        .build())
                    Glide.with(this@SceneDetailActivity)
                        .load(glideUrl)
                        .centerCrop()
                        .into(img)

                    card.isFocusable = true
                    card.setOnClickListener {
                        val intent = Intent(this@SceneDetailActivity, SceneDetailActivity::class.java)
                        intent.putExtra("scene_id", s.id)
                        intent.putExtra("scene_path", s.path)
                        intent.putExtra("scene_duration", s.duration)
                        intent.putExtra("scene_size", s.size)
                        intent.putExtra("scene_width", s.width)
                        intent.putExtra("scene_height", s.height)
                        intent.putExtra("scene_play_count", s.playCount)
                        intent.putExtra("scene_rating100", s.rating100 ?: -1)
                        intent.putExtra("scene_title", s.title)
                        startActivity(intent)
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

    override fun onResume() {
        super.onResume()
        // Refresh resume from Stash
        lifecycleScope.launch {
            try {
                val continuePairs = app.api.findContinuePlaying()
                val match = continuePairs.find { it.first.id == scene.id }
                runOnUiThread {
                    if (match != null && match.second > 5.0) {
                        val resumeMs = (match.second * 1000).toLong()
                        btnResume.visibility = View.VISIBLE
                        btnResume.text = "▶ Resume from ${formatMs(resumeMs)}"
                        btnResume.setOnClickListener {
                            val intent = Intent(this@SceneDetailActivity, PlaybackActivity::class.java)
                            intent.putExtra("scene_id", scene.id)
                            intent.putExtra("scene_title", scene.filename)
                            intent.putExtra("resume_position_ms", resumeMs)
                            startActivity(intent)
                        }
                    } else {
                        btnResume.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateResumeButton() {
        // Initial state — hidden until Stash responds
        btnResume.visibility = View.GONE
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
