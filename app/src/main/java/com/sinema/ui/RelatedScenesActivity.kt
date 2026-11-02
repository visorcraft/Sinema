package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import com.sinema.R
import com.sinema.model.Scene
import java.io.IOException

class RelatedScenesActivity : SinemaActivity() {
    companion object {
        private const val EXTRA_ID = "related_scene_id"
        private const val EXTRA_TITLE = "related_scene_title"

        fun intent(context: Context, source: Scene): Intent =
            Intent(context, RelatedScenesActivity::class.java)
                .putExtra(EXTRA_ID, source.id)
                .putExtra(EXTRA_TITLE, source.displayTitle)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        val fragment = supportFragmentManager.findFragmentById(R.id.main_frame) as? RelatedScenesFragment
            ?: RelatedScenesFragment.create(
                intent.getStringExtra(EXTRA_ID).orEmpty(),
                intent.getStringExtra(EXTRA_TITLE).orEmpty()
            ).also {
                supportFragmentManager.beginTransaction().replace(R.id.main_frame, it).commit()
            }
        bindGridControls(fragment)
        findViewById<Button>(R.id.btn_grid_sort).visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)
}

class RelatedScenesFragment : SceneGridFragment() {
    companion object {
        fun create(id: String, title: String) = RelatedScenesFragment().apply {
            arguments = Bundle().apply {
                putString("id", id)
                putString("title", title)
            }
        }
    }

    override val gridTitle: String
        get() = "Because You Watched · ${requireArguments().getString("title").orEmpty()}"
    override val emptyMessage = "No related scenes found"

    override suspend fun loadItems(): List<Any> {
        val source = app.api.findSceneFull(requireArguments().getString("id").orEmpty())?.scene
            ?: throw IOException("Scene no longer exists")
        return app.api.findRelatedScenes(source)
    }
}
