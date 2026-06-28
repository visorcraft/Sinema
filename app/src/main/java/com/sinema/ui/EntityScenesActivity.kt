package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.EntityItem

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
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            val kind = parseKind(intent.getStringExtra("kind"))
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, EntityScenesFragment.create(
                    kind,
                    intent.getStringExtra("id") ?: "",
                    intent.getStringExtra("name") ?: ""
                ))
                .commit()
        }
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

    private fun parseKind(raw: String?): EntityItem.Kind =
        try {
            raw?.let { EntityItem.Kind.valueOf(it) } ?: EntityItem.Kind.TAG
        } catch (_: IllegalArgumentException) {
            EntityItem.Kind.TAG
        }

    override suspend fun loadItems(): List<Any> {
        // Keep this bounded for TV navigation; server count is available in .first if pagination is added later.
        return app.api.findScenesForEntity(
            kind, requireArguments().getString("id")!!,
            perPage = 200, sort = sort.apiSort(randomSeed), direction = sort.direction
        ).second
    }
}
