package com.sinema.ui

import com.sinema.model.SceneDetails

internal suspend fun loadSceneMetadataForSwitch(
    clear: () -> Unit,
    fetch: suspend () -> SceneDetails?,
    apply: (SceneDetails) -> Unit
) {
    clear()
    fetch()?.let(apply)
}
