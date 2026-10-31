package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sinema.R
import com.sinema.SinemaApp
import com.sinema.model.EntityItem
import com.sinema.model.TagRef
import com.sinema.util.GlideAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EntityScenesActivity : FragmentActivity() {
    companion object {
        fun intent(context: Context, item: EntityItem): Intent =
            Intent(context, EntityScenesActivity::class.java)
                .putExtra("kind", item.kind.name)
                .putExtra("id", item.id)
                .putExtra("name", item.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        // Header binds on every create so it survives recreation; only the
        // fragment creation is skipped when restoring.
        val kind = parseKind(intent.getStringExtra("kind"))
        bindEntityHeader(kind, intent.getStringExtra("id") ?: "", intent.getStringExtra("name") ?: "")
        if (savedInstanceState == null) {
            val fragment = EntityScenesFragment.create(
                kind,
                intent.getStringExtra("id") ?: "",
                intent.getStringExtra("name") ?: ""
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, fragment)
                .commit()
            bindGridControls(fragment)
        } else {
            (supportFragmentManager.findFragmentById(R.id.main_frame) as? SortableScreen)
                ?.let { bindGridControls(it) }
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
        lifecycleScope.launch {
            try {
                val info = SinemaApp.instance.api.findEntityHeader(kind, id) ?: return@launch
                findViewById<TextView>(R.id.entity_header_name).text = info.name
                val count = findViewById<TextView>(R.id.entity_header_count)
                count.text = "${info.sceneCount} " +
                    if (info.sceneCount == 1) "scene" else "scenes"
                val image = findViewById<ImageView>(R.id.entity_header_image)
                if (info.imagePath != null) {
                    Glide.with(this@EntityScenesActivity)
                        .load(GlideAuth.url(SinemaApp.instance.api, info.imagePath))
                        .centerCrop()
                        .into(image)
                } else {
                    image.visibility = View.INVISIBLE
                }
                bindHeaderTagChips(info.tags)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Sinema", "Entity header load failed for $kind $id", e)
            }
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
            val chip = Button(this).apply {
                text = "#${tag.name}"
                isFocusable = true
                setBackgroundResource(R.drawable.action_button_background)
                setTextColor(android.graphics.Color.WHITE)
                textSize = 13f
                minWidth = 0
                minimumWidth = 0
                val hPad = (16 * density).toInt()
                val vPad = (8 * density).toInt()
                setPadding(hPad, vPad, hPad, vPad)
                setOnClickListener {
                    startActivity(EntityScenesActivity.intent(
                        this@EntityScenesActivity,
                        EntityItem(tag.id, tag.name, 0, null, EntityItem.Kind.TAG)
                    ))
                }
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

    private fun parseKind(raw: String?): EntityItem.Kind =
        try {
            raw?.let { EntityItem.Kind.valueOf(it) } ?: EntityItem.Kind.TAG
        } catch (_: IllegalArgumentException) {
            EntityItem.Kind.TAG
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (handleSortMenuKey(keyCode)) true else super.onKeyDown(keyCode, event)
}

class EntityScenesFragment : SceneGridFragment() {
    companion object {
        fun create(kind: EntityItem.Kind, id: String, name: String) = EntityScenesFragment().apply {
            arguments = Bundle().apply {
                putString("kind", kind.name); putString("id", id); putString("name", name)
            }
        }
    }

    private val kind get() = parseKind(requireArguments().getString("kind"))
    override val sortScreenKey: String? get() = "entity_scenes_${kind.name}_${requireArguments().getString("id")}"
    override val gridTitle get() = requireArguments().getString("name") ?: ""
    override val emptyMessage = "No scenes"
    override val canFilter = true
    override val paged = true

    private fun parseKind(raw: String?): EntityItem.Kind =
        try {
            raw?.let { EntityItem.Kind.valueOf(it) } ?: EntityItem.Kind.TAG
        } catch (_: IllegalArgumentException) {
            EntityItem.Kind.TAG
        }

    override suspend fun loadItems(): List<Any> = loadPage(1, pageSize).second

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
}
