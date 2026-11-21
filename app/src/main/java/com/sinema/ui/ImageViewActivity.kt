package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.ImageItem
import com.sinema.util.GlideAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ImageViewActivity : SinemaActivity() {
    private var images: List<ImageItem> = emptyList()
    private var order: List<Int> = emptyList()
    private var state = ImageViewerState()
    private var shuffleEnabled = false
    private var slideshowEnabled = false
    private var slideshowDelayMs = 5_000L
    private var galleryId = ""
    private var autoStartSlideshow = false
    private var loadJob: Job? = null
    private lateinit var imageView: ImageView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var caption: TextView
    private lateinit var status: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var hintView: TextView
    private lateinit var controls: View
    private lateinit var controlsScrim: View
    private val handler = Handler(Looper.getMainLooper())

    private val autoHideControls = Runnable {
        if (!state.isZoomed) {
            setControlsVisible(false)
        }
    }

    private val slideshowTick = object : Runnable {
        override fun run() {
            if (!slideshowEnabled) return
            val next = state.next(order.size)
            if (next.position == state.position) {
                stopSlideshow()
                showControls()
            } else {
                state = next
                showImage()
                scheduleSlideshow()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)
        imageView = findViewById(R.id.fullscreen_image)
        loadingProgress = findViewById(R.id.image_loading_progress)
        caption = findViewById(R.id.image_caption)
        status = findViewById(R.id.image_status)
        progressBar = findViewById(R.id.image_progress_bar)
        hintView = findViewById(R.id.image_hint)
        controls = findViewById(R.id.image_controls)
        controlsScrim = findViewById(R.id.image_controls_scrim)

        galleryId = intent.getStringExtra("gallery_id").orEmpty()
        autoStartSlideshow = intent.getBooleanExtra("auto_start_slideshow", false)
        val initialIndex = savedInstanceState?.getInt("image_index")
            ?: intent.getIntExtra("index", 0)
        state = state.copy(position = initialIndex.coerceAtLeast(0))
        shuffleEnabled = savedInstanceState?.getBoolean("shuffle") ?: false
        slideshowDelayMs = savedInstanceState?.getLong("slideshow_delay") ?: 5_000L
        val displayMode = savedInstanceState?.getString("display_mode")
        state = state.copy(
            displayMode = if (displayMode == ImageDisplayMode.FILL.name)
                ImageDisplayMode.FILL else ImageDisplayMode.FIT,
            zoom = (savedInstanceState?.getFloat("zoom") ?: 1f).coerceIn(1f, 5f),
            panX = savedInstanceState?.getFloat("pan_x") ?: 0f,
            panY = savedInstanceState?.getFloat("pan_y") ?: 0f
        )

        if (galleryId.isBlank()) {
            images = pendingImages
            pendingImages = emptyList()
            prepareImages()
        } else {
            loadGallery()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("image_index", order.getOrElse(state.position) { state.position })
        outState.putBoolean("shuffle", shuffleEnabled)
        outState.putLong("slideshow_delay", slideshowDelayMs)
        outState.putString("display_mode", state.displayMode.name)
        outState.putFloat("zoom", state.zoom)
        outState.putFloat("pan_x", state.panX)
        outState.putFloat("pan_y", state.panY)
    }

    override fun onPause() {
        stopSlideshow()
        handler.removeCallbacks(autoHideControls)
        super.onPause()
    }

    override fun onDestroy() {
        loadJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        controls.animate().cancel()
        controlsScrim.animate().cancel()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                showOptions()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (controls.isVisible) {
                    setControlsVisible(false)
                } else {
                    showControls()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                toggleSlideshow()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                moveNext()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                movePrevious()
                return true
            }
            KeyEvent.KEYCODE_ZOOM_IN, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> {
                state = state.zoomIn()
                applyTransform()
                showControls()
                return true
            }
            KeyEvent.KEYCODE_ZOOM_OUT, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                state = state.zoomOut()
                applyTransform()
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> {
                if (state.isZoomed) {
                    val step = 80f
                    val (dx, dy) = when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> -step to 0f
                        KeyEvent.KEYCODE_DPAD_LEFT -> step to 0f
                        KeyEvent.KEYCODE_DPAD_DOWN -> 0f to -step
                        else -> 0f to step
                    }
                    state = state.pan(dx, dy, imageView.width, imageView.height)
                    applyTransform()
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    moveNext()
                } else {
                    movePrevious()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadGallery() {
        loadingProgress.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        caption.text = "Loading gallery…"
        status.text = ""
        loadJob = lifecycleScope.launch {
            try {
                val loaded = mutableListOf<ImageItem>()
                var count = Int.MAX_VALUE
                var page = 1
                while (loaded.size < count && loaded.size < MAX_GALLERY_IMAGES) {
                    val result = SinemaApp.instance.api.findImagesForGallery(galleryId, page, GALLERY_PAGE_SIZE)
                    count = result.first
                    if (result.second.isEmpty()) break
                    loaded.addAll(result.second)
                    page++
                }
                loadingProgress.visibility = View.GONE
                images = loaded.take(MAX_GALLERY_IMAGES)
                prepareImages()
                if (loaded.size < count) {
                    Toast.makeText(
                        this@ImageViewActivity,
                        "Large gallery: showing the first $MAX_GALLERY_IMAGES images",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadingProgress.visibility = View.GONE
                Log.e("Sinema", "Failed to load gallery $galleryId", e)
                Toast.makeText(this@ImageViewActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun prepareImages() {
        if (images.isEmpty()) {
            finish()
            return
        }
        val current = state.position.coerceIn(0, images.lastIndex)
        order = if (shuffleEnabled) {
            shuffledImageOrder(images.size, current, System.currentTimeMillis())
        } else {
            (images.indices).toList()
        }
        state = state.copy(position = current)
        showImage()
        if (autoStartSlideshow) {
            autoStartSlideshow = false
            startSlideshow()
        } else {
            scheduleAutoHide()
        }
    }

    private fun moveNext() {
        val next = state.next(order.size)
        if (next == state) return
        state = next
        showImage()
        if (slideshowEnabled) scheduleSlideshow() else if (controls.isVisible) scheduleAutoHide()
    }

    private fun movePrevious() {
        val previous = state.previous(order.size)
        if (previous == state) return
        state = previous
        showImage()
        if (slideshowEnabled) scheduleSlideshow() else if (controls.isVisible) scheduleAutoHide()
    }

    private fun showImage() {
        val imageIndex = order.getOrElse(state.position) { return }
        val image = images[imageIndex]
        caption.text = "${image.title.ifBlank { image.filename }}  (${imageIndex + 1}/${images.size})"
        applyTransform()
        val api = SinemaApp.instance.api
        Glide.with(this)
            .load(GlideAuth.url(api, api.getImageUrl(image.id)))
            .dontTransform()
            .into(imageView)
    }

    private fun applyTransform() {
        imageView.scaleType = if (state.displayMode == ImageDisplayMode.FIT)
            ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        imageView.scaleX = state.zoom
        imageView.scaleY = state.zoom
        imageView.translationX = state.panX
        imageView.translationY = state.panY
        status.text = state.statusSummary(shuffleEnabled, slideshowEnabled, slideshowDelayMs)
        val permille = state.progressPermille(order.size)
        if (order.size > 1) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = permille
        } else {
            progressBar.visibility = View.GONE
        }
        hintView.setText(
            when (state.hintMode(slideshowEnabled)) {
                ImageViewerHintMode.BROWSE -> R.string.screens_polish_image_hint_browse
                ImageViewerHintMode.ZOOMED -> R.string.screens_polish_image_hint_zoomed
                ImageViewerHintMode.SLIDESHOW -> R.string.screens_polish_image_hint_slideshow
            }
        )
    }

    private fun showControls() {
        applyTransform()
        setControlsVisible(true)
        scheduleAutoHide()
    }

    private fun setControlsVisible(visible: Boolean) {
        handler.removeCallbacks(autoHideControls)
        val reduceMotion = SinemaApp.instance.prefs.reduceMotion
        listOf(controls, controlsScrim).forEach { panel ->
            panel.animate().cancel()
            if (reduceMotion) {
                panel.alpha = 1f
                panel.isVisible = visible
            } else if (visible) {
                if (!panel.isVisible) {
                    panel.alpha = 0f
                    panel.isVisible = true
                }
                panel.animate().alpha(1f).setDuration(180L).start()
            } else if (panel.isVisible) {
                panel.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction {
                        panel.isVisible = false
                        panel.alpha = 1f
                    }
                    .start()
            }
        }
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHideControls)
        if (!state.isZoomed && !slideshowEnabled && controls.isVisible) {
            handler.postDelayed(autoHideControls, HUD_AUTO_HIDE_MS)
        }
    }

    private fun showOptions() {
        stopSlideshow()
        handler.removeCallbacks(autoHideControls)
        Dialogs.showActions(
            this,
            "Image viewer",
            listOf(
                (if (slideshowEnabled) "Pause slideshow" else "Start slideshow") to ::toggleSlideshow,
                "Slideshow speed: ${slideshowDelayMs / 1000}s" to {
                    slideshowDelayMs = nextSlideshowDelay(slideshowDelayMs)
                    if (slideshowEnabled) scheduleSlideshow()
                    showControls()
                },
                (if (shuffleEnabled) "Shuffle: On" else "Shuffle: Off") to ::toggleShuffle,
                (if (state.displayMode == ImageDisplayMode.FIT) "Display: Fit screen" else "Display: Fill screen") to {
                    state = state.toggleDisplayMode()
                    showImage()
                    showControls()
                },
                "Zoom in" to {
                    state = state.zoomIn()
                    applyTransform()
                    showControls()
                },
                "Zoom out" to {
                    state = state.zoomOut()
                    applyTransform()
                    showControls()
                },
                "Reset zoom" to {
                    state = state.resetTransform()
                    applyTransform()
                    showControls()
                }
            )
        )
    }

    private fun toggleShuffle() {
        val current = order.getOrElse(state.position) { return }
        shuffleEnabled = !shuffleEnabled
        order = if (shuffleEnabled) {
            shuffledImageOrder(images.size, current, System.currentTimeMillis())
        } else {
            images.indices.toList()
        }
        state = state.copy(position = current).resetTransform()
        showImage()
        showControls()
    }

    private fun toggleSlideshow() {
        if (slideshowEnabled) {
            stopSlideshow()
            showControls()
        } else {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        if (images.size < 2) {
            Toast.makeText(this, "No next image for slideshow", Toast.LENGTH_SHORT).show()
            return
        }
        slideshowEnabled = true
        setControlsVisible(false)
        scheduleSlideshow()
    }

    private fun stopSlideshow() {
        slideshowEnabled = false
        handler.removeCallbacks(slideshowTick)
    }

    private fun scheduleSlideshow() {
        handler.removeCallbacks(slideshowTick)
        handler.postDelayed(slideshowTick, slideshowDelayMs)
    }

    companion object {
        private const val GALLERY_PAGE_SIZE = 500
        private const val MAX_GALLERY_IMAGES = 10_000
        private const val HUD_AUTO_HIDE_MS = 4_500L
        var pendingImages: List<ImageItem> = emptyList()

        fun intentFor(context: Context, images: List<ImageItem>, startIndex: Int): Intent {
            pendingImages = images
            return Intent(context, ImageViewActivity::class.java)
                .putExtra("index", startIndex.coerceAtLeast(0))
        }

        fun intentForGallery(
            context: Context,
            galleryId: String,
            startIndex: Int,
            autoStartSlideshow: Boolean = false
        ): Intent {
            pendingImages = emptyList()
            return Intent(context, ImageViewActivity::class.java)
                .putExtra("gallery_id", galleryId)
                .putExtra("index", startIndex.coerceAtLeast(0))
                .putExtra("auto_start_slideshow", autoStartSlideshow)
        }
    }
}
