package com.sinema.ui

import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.sinema.model.SubtitleBackground
import com.sinema.model.SubtitlePosition
import com.sinema.model.SubtitleSize
import com.sinema.util.Prefs

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object SubtitleAppearance {
    private const val DEFAULT_TEXT_FRACTION = 0.0533f
    private const val STRONG_SHADOW_COLOR = 0xE6000000.toInt()
    private const val SUBTLE_SHADOW_COLOR = 0x99000000.toInt()

    internal fun buildCaptionStyle(background: SubtitleBackground): CaptionStyleCompat {
        val edgeColor = if (background.alpha <= SubtitleBackground.HALF.alpha) {
            STRONG_SHADOW_COLOR
        } else {
            SUBTLE_SHADOW_COLOR
        }
        return CaptionStyleCompat(
            Color.WHITE,
            Color.argb(background.alpha, 0, 0, 0),
            Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            edgeColor,
            null
        )
    }

    fun apply(playerView: PlayerView, prefs: Prefs) {
        val subtitleView = playerView.subtitleView ?: return
        val size = SubtitleSize.fromName(prefs.subtitleSize)
        val background = SubtitleBackground.fromName(prefs.subtitleBackground)
        val position = SubtitlePosition.fromName(prefs.subtitlePosition)
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setFractionalTextSize(DEFAULT_TEXT_FRACTION * size.scale)
        subtitleView.setBottomPaddingFraction(position.bottomPadding)
        subtitleView.setStyle(buildCaptionStyle(background))
    }
}
