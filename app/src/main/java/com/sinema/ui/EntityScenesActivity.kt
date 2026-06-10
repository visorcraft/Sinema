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
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, EntityScenesFragment.create(
                    EntityItem.Kind.valueOf(intent.getStringExtra("kind") ?: EntityItem.Kind.TAG.name),
                    intent.getStringExtra("id") ?: "",
                    intent.getStringExtra("name") ?: ""
                ))
                .commit()
        }
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

    private val kind get() = EntityItem.Kind.valueOf(requireArguments().getString("kind")!!)
    override val sortScreenKey = "entity_scenes"
    override val gridTitle get() = requireArguments().getString("name") ?: ""
    override val emptyMessage = "No scenes"

    override suspend fun loadItems(): List<Any> {
        // TODO: paginate; silently capped at 200 (server count available in .first)
        return app.api.findScenesForEntity(
            kind, requireArguments().getString("id")!!,
            perPage = 200, sort = sort.apiSort(randomSeed), direction = sort.direction
        ).second
    }
}
