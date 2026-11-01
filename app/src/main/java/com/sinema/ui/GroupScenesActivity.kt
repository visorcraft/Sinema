package com.sinema.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.GroupItem
import com.sinema.model.Scene
import com.sinema.util.GlideAuth
import com.sinema.util.loadAuth
import com.sinema.util.SceneIntents
import java.io.IOException

class GroupScenesActivity : FragmentActivity() {
    companion object {
        private const val EXTRA_ID = "group_id"
        private const val EXTRA_NAME = "group_name"
        private const val EXTRA_COUNT = "group_count"
        private const val EXTRA_IMAGE = "group_image"
        private const val EXTRA_DATE = "group_date"
        private const val EXTRA_DIRECTOR = "group_director"
        private const val EXTRA_SYNOPSIS = "group_synopsis"
        private const val EXTRA_STUDIO = "group_studio"

        fun intent(context: Context, group: GroupItem): Intent =
            Intent(context, GroupScenesActivity::class.java)
                .putExtra(EXTRA_ID, group.id)
                .putExtra(EXTRA_NAME, group.name)
                .putExtra(EXTRA_COUNT, group.sceneCount)
                .putExtra(EXTRA_IMAGE, group.frontImagePath)
                .putExtra(EXTRA_DATE, group.date)
                .putExtra(EXTRA_DIRECTOR, group.director)
                .putExtra(EXTRA_SYNOPSIS, group.synopsis)
                .putExtra(EXTRA_STUDIO, group.studioName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        bindHeader()
        val fragment = supportFragmentManager.findFragmentById(R.id.main_frame) as? GroupScenesFragment
            ?: GroupScenesFragment.create(
                intent.getStringExtra(EXTRA_ID).orEmpty(),
                intent.getStringExtra(EXTRA_NAME).orEmpty()
            ).also {
                supportFragmentManager.beginTransaction().replace(R.id.main_frame, it).commit()
            }
        bindGridControls(fragment)
        findViewById<Button>(R.id.btn_grid_sort).visibility = View.GONE
        findViewById<Button>(R.id.btn_group_resume).apply {
            visibility = View.VISIBLE
            setOnClickListener { fragment.playResume() }
        }
        // Collapse the group header while the grid scrolls; restore at top.
        val frame = findViewById<View>(R.id.main_frame)
        frame.post {
            frame.findRecyclerView()?.let { grid ->
                findViewById<View>(R.id.entity_header)?.let { header ->
                    CollapsingHeader.bind(grid, header)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)

    private fun bindHeader() {
        findViewById<View>(R.id.entity_header).visibility = View.VISIBLE
        findViewById<TextView>(R.id.entity_header_name).text = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val count = intent.getIntExtra(EXTRA_COUNT, 0)
        val meta = buildList {
            add("$count " + if (count == 1) "scene" else "scenes")
            intent.getStringExtra(EXTRA_DATE)?.takeIf { it.isNotBlank() }?.let(::add)
            intent.getStringExtra(EXTRA_DIRECTOR)?.takeIf { it.isNotBlank() }?.let(::add)
            intent.getStringExtra(EXTRA_STUDIO)?.takeIf { it.isNotBlank() }?.let(::add)
        }
        findViewById<TextView>(R.id.entity_header_count).text = meta.joinToString("  •  ")
        val synopsis = intent.getStringExtra(EXTRA_SYNOPSIS).orEmpty()
        findViewById<TextView>(R.id.entity_header_details).apply {
            text = synopsis
            visibility = if (synopsis.isBlank()) View.GONE else View.VISIBLE
        }
        val imagePath = intent.getStringExtra(EXTRA_IMAGE)
        val image = findViewById<ImageView>(R.id.entity_header_image)
        if (imagePath.isNullOrBlank()) {
            image.visibility = View.INVISIBLE
        } else {
            image.loadAuth(SinemaApp.instance.api, imagePath)
        }
    }
}

class GroupScenesFragment : SceneGridFragment() {
    companion object {
        fun create(id: String, name: String) = GroupScenesFragment().apply {
            arguments = Bundle().apply {
                putString("id", id)
                putString("name", name)
            }
        }
    }

    override val gridTitle: String get() = requireArguments().getString("name").orEmpty()
    override val emptyMessage = "No scenes in this group"

    override suspend fun loadItems(): List<Any> =
        app.api.findGroupDetails(requireArguments().getString("id").orEmpty())?.scenes
            ?: throw IOException("Group no longer exists")

    override fun showGridMenu() {
        AlertDialog.Builder(requireContext())
            .setTitle(gridTitle)
            .setItems(arrayOf("Play All", "Shuffle Play")) { dialog, which ->
                dialog.dismiss()
                playAll(shuffle = which == 1)
            }
            .show()
    }

    fun playResume() {
        val scenes = (0 until gridAdapter.size()).mapNotNull { gridAdapter.get(it) as? Scene }
        val index = GroupPlayback.resumeIndex(scenes)
        if (index < 0) {
            Toast.makeText(requireContext(), emptyMessage, Toast.LENGTH_SHORT).show()
            return
        }
        SceneIntents.playAll(
            requireContext(),
            scenes,
            startAt = index,
            resumeMs = GroupPlayback.resumePositionMs(scenes[index])
        )
    }
}
