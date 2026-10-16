package com.sinema.model

enum class PlaybackAspectRatio(val label: String, private val fixedRatio: Float?) {
    AUTO("Auto (source)", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_9_16("9:16", 9f / 16f);

    fun effectiveRatio(sourceRatio: Float): Float = fixedRatio ?: sourceRatio

    companion object {
        fun fromName(name: String?): PlaybackAspectRatio =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}
