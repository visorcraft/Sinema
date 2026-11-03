package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityItem
import com.sinema.model.TagRef
import com.sinema.util.GlideAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EntityScenesActivity : GridActivity() {
    companion object {
        fun intent(context: Context, item: EntityItem): Intent =
            Intent(context, EntityScenesActivity::class.java)
                .putExtra("kind", item.kind.name)
                .putExtra("id", item.id)
                .putExtra("name", item.name)
    }

    override fun createGridFragment(): SceneGridFragment = EntityScenesFragment.create(
        EntityItem.parseOrTag(intent.getStringExtra("kind")),
        intent.getStringExtra("id") ?: "",
        intent.getStringExtra("name") ?: ""
    )

    override fun onGridBound(screen: SceneGridFragment) {
        // Header binds on every create so it survives recreation.
        bindEntityHeader(
            EntityItem.parseOrTag(intent.getStringExtra("kind")),
            intent.getStringExtra("id") ?: "",
            intent.getStringExtra("name") ?: ""
        )
        // Collapse the tall entity header while the grid is scrolled so
        // content rows get the full viewport; it returns at the top. Runs on
        // every create so restored fragments keep the behavior.
        val frame = findViewById<View>(R.id.main_frame)
        frame.post {
            frame.findRecyclerView()?.let { grid ->
                findViewById<View>(R.id.entity_header)?.let { header ->
                    CollapsingHeader.bind(grid, header)
                }
            }
        }
    }

    /**
     * Detail header above the grid: name immediately (from intent extras),
     * then image, scene count, and — for performers — tag chips loaded from
     * Stash. Degrades silently when the header query fails.
     */
    private fun bindEntityHeader(kind: EntityItem.Kind, id: String, name: String) {
        val header = findViewById<View>(R.id.entity_header) ?: return
        header.visibility = View.VISIBLE
        findViewById<TextView>(R.id.entity_header_name).text = name
        findViewById<TextView>(R.id.entity_header_count).text = kind.label
        launchSafely("Entity header load", onError = {}) {
            val info = SinemaApp.instance.api.findEntityHeader(kind, id) ?: return@launchSafely
            findViewById<TextView>(R.id.entity_header_name).text = info.name
            val count = findViewById<TextView>(R.id.entity_header_count)
            count.text = "${info.sceneCount} " +
                if (info.sceneCount == 1) "scene" else "scenes"
            val image = findViewById<ImageView>(R.id.entity_header_image)
            val headerImageUrl = info.imagePath
                ?: SinemaApp.instance.api.entityImageUrl(kind, id)
            Glide.with(this@EntityScenesActivity)
                .load(GlideAuth.url(SinemaApp.instance.api, headerImageUrl))
                .centerCrop()
                .error(android.R.drawable.ic_menu_gallery)
                .into(image)
            bindHeaderTagChips(info.tags)
            bindHeaderActions(kind, info)
        }
    }

    private fun bindHeaderActions(kind: EntityItem.Kind, info: com.sinema.model.EntityHeader) {
        val actions = findViewById<View>(R.id.entity_header_actions) ?: return
        actions.visibility = View.VISIBLE
        val favoriteButton = findViewById<Button>(R.id.entity_header_favorite)
        fun favoriteLabel(): String =
            if (info.isFavorite) "♥ Favorited ✓" else "♡ Favorite"
        favoriteButton.text = favoriteLabel()
        favoriteButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    SinemaApp.instance.api.entitySetRating(
                        kind, info.id, if (info.isFavorite) null else 100
                    )
                    // Reflect locally: EntityHeader is immutable here, so
                    // rebuild the label from the toggled copy.
                    val toggled = info.copy(rating100 = if (info.isFavorite) null else 100)
                    bindHeaderActions(kind, toggled)
                    android.widget.Toast.makeText(
                        this@EntityScenesActivity,
                        if (info.isFavorite) "Removed from favorites" else "Added to favorites",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("Sinema", "Entity favorite toggle failed for ${info.id}", e)
                    android.widget.Toast.makeText(
                        this@EntityScenesActivity,
                        "Server does not support entity ratings: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        findViewById<Button>(R.id.entity_header_play_unwatched).setOnClickListener {
            (supportFragmentManager.findFragmentById(R.id.main_frame) as? EntityScenesFragment)
                ?.playUnwatchedFromGrid()
        }
    }

    private fun bindHeaderTagChips(tags: List<TagRef>) {
        // Clear before rebinding — the header can rebind after recreation.
        val row = findViewById<LinearLayout>(R.id.entity_header_tags_row)
        row.removeAllViews()
        if (tags.isEmpty()) return
        val scroll = findViewById<View>(R.id.entity_header_tags_scroll)
        val density = resources.displayMetrics.density
        for (tag in tags) {
            val chip = tvChip("#${tag.name}") {
                startActivity(EntityScenesActivity.intent(
                    this@EntityScenesActivity,
                    EntityItem(tag.id, tag.name, 0, null, EntityItem.Kind.TAG)
                ))
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = (8 * density).toInt()
            row.addView(chip, params)
        }
        scroll.visibility = View.VISIBLE
    }
}

class EntityScenesFragment : SceneGridFragment() {
    companion object {
        fun create(kind: EntityItem.Kind, id: String, name: String) = EntityScenesFragment().apply {
            arguments = Bundle().apply {
                putString("kind", kind.name); putString("id", id); putString("name", name)
            }
        }
    }

    private val kind get() = EntityItem.parseOrTag(requireArguments().getString("kind"))
    override val sortScreenKey: String? get() = "entity_scenes_${kind.name}_${requireArguments().getString("id")}"
    override val gridTitle get() = requireArguments().getString("name") ?: ""
    override val emptyMessage = "No scenes"
    override val canFilter = true
    override val paged = true


    override suspend fun loadPage(page: Int, perPage: Int): Pair<Int, List<Any>> =
        app.api.findScenesForEntity(
            kind,
            requireArguments().getString("id")!!,
            page = page,
            perPage = perPage,
            sort = sort.apiSort(randomSeed),
            direction = sort.direction,
            filters = sceneFilter
        )

    /** Header action: shuffle-play the unwatched scenes currently loaded. */
    fun playUnwatchedFromGrid() {
        val unwatched = (0 until gridAdapter.size())
            .mapNotNull { gridAdapter.get(it) as? com.sinema.model.Scene }
            .filter { !it.isWatched }
        if (unwatched.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), getString(com.sinema.R.string.entity_no_unwatched), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        com.sinema.util.SceneIntents.playAll(requireContext(), unwatched, shuffle = true)
    }
}

/** Depth-first RecyclerView lookup for grid fragments hosted in a frame. */
internal fun View.findRecyclerView(): androidx.recyclerview.widget.RecyclerView? {
    if (this is androidx.recyclerview.widget.RecyclerView) return this
    if (this is android.view.ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findRecyclerView()?.let { return it }
        }
    }
    return null
}
