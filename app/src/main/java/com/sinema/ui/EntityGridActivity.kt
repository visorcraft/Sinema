package com.sinema.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.sinema.R
import com.sinema.model.EntityItem

class EntityGridActivity : FragmentActivity() {
    companion object {
        fun intent(context: Context, kind: EntityItem.Kind): Intent =
            Intent(context, EntityGridActivity::class.java).putExtra("kind", kind.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            val kind = parseKind(intent.getStringExtra("kind"))
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, EntityGridFragment.create(kind))
                .commit()
        }
    }

    private fun parseKind(raw: String?): EntityItem.Kind =
        try {
            raw?.let { EntityItem.Kind.valueOf(it) } ?: EntityItem.Kind.TAG
        } catch (_: IllegalArgumentException) {
            EntityItem.Kind.TAG
        }
}

class EntityGridFragment : SceneGridFragment() {
    companion object {
        fun create(kind: EntityItem.Kind) = EntityGridFragment().apply {
            arguments = Bundle().apply { putString("kind", kind.name) }
        }
    }

    private val kind get() = parseKind(requireArguments().getString("kind"))

    private fun parseKind(raw: String?): EntityItem.Kind =
        try {
            raw?.let { EntityItem.Kind.valueOf(it) } ?: EntityItem.Kind.TAG
        } catch (_: IllegalArgumentException) {
            EntityItem.Kind.TAG
        }
    override val columns = 4
    override val gridTitle get() = kind.label
    override val emptyMessage get() = "No ${kind.label.lowercase()} found"

    override suspend fun loadItems(): List<Any> {
        // TODO: paginate; silently capped at 200 (server count available in .first)
        return app.api.findEntities(kind, perPage = 200).second
    }

    override fun onItemClicked(item: Any) {
        if (item is EntityItem) startActivity(EntityScenesActivity.intent(requireContext(), item))
    }
}
