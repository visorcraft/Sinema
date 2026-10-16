package com.sinema.ui

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sinema.model.PlaybackAspectRatio

@OptIn(UnstableApi::class)
class AspectRatioPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {
    private var aspectRatioFrame: AspectRatioFrameLayout? = null
    private var sourceAspectRatio = 0f

    var playbackAspectRatio: PlaybackAspectRatio = PlaybackAspectRatio.AUTO
        set(value) {
            field = value
            applyAspectRatio()
        }

    override fun onContentAspectRatioChanged(
        contentFrame: AspectRatioFrameLayout?,
        aspectRatio: Float
    ) {
        aspectRatioFrame = contentFrame
        sourceAspectRatio = aspectRatio
        applyAspectRatio()
    }

    private fun applyAspectRatio() {
        aspectRatioFrame?.setAspectRatio(playbackAspectRatio.effectiveRatio(sourceAspectRatio))
    }
}
