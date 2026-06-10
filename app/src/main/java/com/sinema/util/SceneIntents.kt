package com.sinema.util

import android.content.Context
import android.content.Intent
import com.sinema.model.Scene
import com.sinema.ui.SceneDetailActivity

/** Single source of truth for passing a Scene between activities via Intent extras. */
object SceneIntents {
    fun detail(context: Context, scene: Scene): Intent =
        Intent(context, SceneDetailActivity::class.java).apply {
            putExtra("scene_id", scene.id)
            putExtra("scene_title", scene.title)
            putExtra("scene_path", scene.path)
            putExtra("scene_duration", scene.duration)
            putExtra("scene_size", scene.size)
            putExtra("scene_width", scene.width)
            putExtra("scene_height", scene.height)
            putExtra("scene_play_count", scene.playCount)
            putExtra("scene_rating100", scene.rating100 ?: -1)
        }

    fun sceneFrom(intent: Intent): Scene = Scene(
        id = intent.getStringExtra("scene_id") ?: "",
        title = intent.getStringExtra("scene_title") ?: "",
        path = intent.getStringExtra("scene_path") ?: "",
        duration = intent.getDoubleExtra("scene_duration", 0.0),
        size = intent.getLongExtra("scene_size", 0L),
        width = intent.getIntExtra("scene_width", 0),
        height = intent.getIntExtra("scene_height", 0),
        playCount = intent.getIntExtra("scene_play_count", 0),
        rating100 = intent.getIntExtra("scene_rating100", -1).takeIf { it != -1 }
    )
}
