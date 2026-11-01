package com.sinema.util

/** Pure routing for com.sinema://app/… deep links. */
object DeepLinks {
    /** Watch Next links: /play/&lt;id&gt; resumes playback directly. */
    fun playSceneId(path: String?): String? =
        path?.removePrefix("/play/")?.takeIf { it.isNotBlank() && "/" !in it }

    /** Standard links: /scene/&lt;id&gt; opens the detail screen. */
    fun detailSceneId(path: String?): String? =
        path?.removePrefix("/scene/")?.takeIf { it.isNotBlank() && "/" !in it }
}
