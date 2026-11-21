package com.sinema.ui

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.sinema.R

class RemoteGuideActivity : SinemaActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(40), dp(48), dp(40))
            setBackgroundColor(0xFF1B1B1B.toInt())
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.remote_guide_title)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, dp(16))
        })
        addSection(R.string.remote_guide_navigation_title, R.string.remote_guide_navigation_body, content)
        addSection(R.string.remote_guide_grids_title, R.string.remote_guide_grids_body, content)
        addSection(R.string.remote_guide_playback_title, R.string.remote_guide_playback_body, content)
        addSection(R.string.remote_guide_queues_title, R.string.remote_guide_queues_body, content)
        addSection(R.string.remote_guide_images_title, R.string.remote_guide_images_body, content)
        addSection(R.string.remote_guide_accessibility_title, R.string.remote_guide_accessibility_body, content)
        setContentView(ScrollView(this).apply {
            tag = "remote_guide_scroll"
            setBackgroundColor(0xFF1B1B1B.toInt())
            addView(content)
        })
    }

    private fun addSection(titleRes: Int, bodyRes: Int, content: LinearLayout) {
        content.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(20), 0, dp(6))
        })
        content.addView(TextView(this).apply {
            text = getString(bodyRes)
            textSize = 17f
            setTextColor(0xFFDDDDDD.toInt())
            setLineSpacing(0f, 1.25f)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isFocusable = true
            bindFocusBackground(normal = 0xFF20282D.toInt(), focused = 0xFF2AABE0.toInt())
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
