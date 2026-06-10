package com.sinema.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.ImageItem
import com.sinema.util.GlideAuth

class ImageViewActivity : FragmentActivity() {
    private var images: List<ImageItem> = emptyList()
    private var index = 0
    private lateinit var imageView: ImageView
    private lateinit var caption: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)
        imageView = findViewById(R.id.fullscreen_image)
        caption = findViewById(R.id.image_caption)

        // Via static holder, not Intent extras: large folders (thousands of
        // images) would exceed the 1 MB binder transaction limit.
        images = pendingImages
        index = (savedInstanceState?.getInt("index") ?: intent.getIntExtra("index", 0))
            .coerceIn(0, (images.size - 1).coerceAtLeast(0))

        if (images.isEmpty()) {
            // Nothing to show (e.g. relaunched after process death).
            finish()
            return
        }
        showImage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", index)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (index < images.size - 1) {
                    index++
                    showImage()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> {
                if (index > 0) {
                    index--
                    showImage()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showImage() {
        val image = images[index]
        caption.text = "${image.filename}  (${index + 1}/${images.size})"

        val api = SinemaApp.instance.api
        Glide.with(this)
            .load(GlideAuth.url(api, api.getImageUrl(image.id)))
            .fitCenter()
            .into(imageView)
    }

    companion object {
        var pendingImages: List<ImageItem> = emptyList()

        fun intentFor(context: android.content.Context, images: List<ImageItem>, startIndex: Int): android.content.Intent {
            pendingImages = images
            return android.content.Intent(context, ImageViewActivity::class.java)
                .putExtra("index", startIndex.coerceAtLeast(0))
        }
    }
}
