package com.sinema.model

enum class InterfaceTextScale(val label: String, val fontScale: Float) {
    DEFAULT("Default", 1.0f),
    LARGE("Large", 1.2f),
    EXTRA_LARGE("Extra large", 1.4f);

    fun next(): InterfaceTextScale = entries[(ordinal + 1) % entries.size]

    fun effectiveFontScale(systemScale: Float): Float = systemScale * fontScale

    companion object {
        fun fromName(value: String): InterfaceTextScale =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
