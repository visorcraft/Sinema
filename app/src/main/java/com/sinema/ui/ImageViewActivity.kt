package com.sinema.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.sinema.R
import com.sinema.SinemaApp

class ImageViewActivity : FragmentActivity() {
    private var imageIds: List<String> = emptyList()
    private var imageNames: List<String> = emptyList()
    private var index = 0
    private lateinit var imageView: ImageView
    private lateinit var caption: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)
        imageView = findViewById(R.id.fullscreen_image)
        caption = findViewById(R.id.image_caption)

        imageIds = intent.getStringArrayListExtra("image_ids") ?: emptyList()
        imageNames = intent.getStringArrayListExtra("image_names") ?: emptyList()
        index = (savedInstanceState?.getInt("index") ?: intent.getIntExtra("index", 0))
            .coerceIn(0, (imageIds.size - 1).coerceAtLeast(0))

        if (imageIds.isEmpty()) {
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
                if (index < imageIds.size - 1) {
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
        val id = imageIds[index]
        val name = imageNames.getOrNull(index) ?: ""
        caption.text = "$name  (${index + 1}/${imageIds.size})"

        val url = SinemaApp.instance.api.getImageUrl(id)
        val glideUrl = GlideUrl(url, LazyHeaders.Builder()
            .addHeader("ApiKey", SinemaApp.instance.prefs.apiKey)
            .build())
        Glide.with(this)
            .load(glideUrl)
            .fitCenter()
            .into(imageView)
    }
}
