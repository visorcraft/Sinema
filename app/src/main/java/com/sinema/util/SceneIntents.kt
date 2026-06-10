package com.sinema.util

import android.content.Context
import android.content.Intent
import com.sinema.model.CaptionRef
import com.sinema.model.MarkerRef
import com.sinema.model.Scene
import com.sinema.ui.SceneDetailActivity

/**
 * Single source of truth for passing a Scene between activities via Intent extras.
 * Scene metadata (tags/performers/captions/etc.) is NOT carried across the round-trip — receivers needing it must refetch via the full scene query, except via the explicit putCaptions/captionsFrom pair used for playback intents.
 */
object SceneIntents {
    private const val KEY_ID = "scene_id"
    private const val KEY_TITLE = "scene_title"
    private const val KEY_PATH = "scene_path"
    private const val KEY_DURATION = "scene_duration"
    private const val KEY_SIZE = "scene_size"
    private const val KEY_WIDTH = "scene_width"
    private const val KEY_HEIGHT = "scene_height"
    private const val KEY_PLAY_COUNT = "scene_play_count"
    private const val KEY_RATING = "scene_rating100"
    private const val KEY_CAPTION_LANGS = "caption_langs"
    private const val KEY_CAPTION_TYPES = "caption_types"
    private const val KEY_MARKER_TITLES = "marker_titles"
    private const val KEY_MARKER_SECONDS = "marker_seconds"

    fun detail(context: Context, scene: Scene): Intent =
        Intent(context, SceneDetailActivity::class.java).apply {
            putExtra(KEY_ID, scene.id)
            putExtra(KEY_TITLE, scene.title)
            putExtra(KEY_PATH, scene.path)
            putExtra(KEY_DURATION, scene.duration)
            putExtra(KEY_SIZE, scene.size)
            putExtra(KEY_WIDTH, scene.width)
            putExtra(KEY_HEIGHT, scene.height)
            putExtra(KEY_PLAY_COUNT, scene.playCount)
            putExtra(KEY_RATING, scene.rating100 ?: -1)
        }

    fun sceneFrom(intent: Intent): Scene = Scene(
        id = intent.getStringExtra(KEY_ID) ?: "",
        title = intent.getStringExtra(KEY_TITLE) ?: "",
        path = intent.getStringExtra(KEY_PATH) ?: "",
        duration = intent.getDoubleExtra(KEY_DURATION, 0.0),
        size = intent.getLongExtra(KEY_SIZE, 0L),
        width = intent.getIntExtra(KEY_WIDTH, 0),
        height = intent.getIntExtra(KEY_HEIGHT, 0),
        playCount = intent.getIntExtra(KEY_PLAY_COUNT, 0),
        rating100 = intent.getIntExtra(KEY_RATING, -1).takeIf { it != -1 }
    )

    fun putCaptions(intent: Intent, captions: List<CaptionRef>) {
        intent.putExtra(KEY_CAPTION_LANGS, captions.map { it.languageCode }.toTypedArray())
        intent.putExtra(KEY_CAPTION_TYPES, captions.map { it.captionType }.toTypedArray())
    }

    fun captionsFrom(intent: Intent): List<CaptionRef> {
        val langs = intent.getStringArrayExtra(KEY_CAPTION_LANGS) ?: return emptyList()
        val types = intent.getStringArrayExtra(KEY_CAPTION_TYPES) ?: return emptyList()
        if (langs.size != types.size) return emptyList()
        return langs.zip(types).map { (l, t) -> CaptionRef(l, t) }
    }

    fun putMarkers(intent: Intent, markers: List<MarkerRef>) {
        val sorted = markers.sortedBy { it.seconds }
        intent.putExtra(KEY_MARKER_TITLES, sorted.map { it.title.ifBlank { it.primaryTag } }.toTypedArray())
        intent.putExtra(KEY_MARKER_SECONDS, sorted.map { it.seconds }.toDoubleArray())
    }

    fun markersFrom(intent: Intent): List<Pair<String, Double>> {
        val titles = intent.getStringArrayExtra(KEY_MARKER_TITLES) ?: return emptyList()
        val seconds = intent.getDoubleArrayExtra(KEY_MARKER_SECONDS) ?: return emptyList()
        if (titles.size != seconds.size) return emptyList()
        return titles.zip(seconds.toList())
    }
}
