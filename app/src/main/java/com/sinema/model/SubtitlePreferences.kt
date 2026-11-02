package com.sinema.model

enum class SubtitleSize(val label: String, val scale: Float) {
    SMALL("Small", 0.75f),
    NORMAL("Normal", 1.0f),
    LARGE("Large", 1.5f),
    EXTRA_LARGE("Extra large", 2.0f);

    fun next(): SubtitleSize = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(value: String): SubtitleSize = entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

enum class SubtitleBackground(val label: String, val alpha: Int) {
    OFF("Off", 0),
    HALF("50%", 128),
    STRONG("80%", 204),
    OPAQUE("100%", 255);

    fun next(): SubtitleBackground = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(value: String): SubtitleBackground = entries.firstOrNull { it.name == value } ?: STRONG
    }
}

enum class SubtitlePosition(val label: String, val bottomPadding: Float) {
    LOW("Low", 0.05f),
    MIDDLE("Middle", 0.12f),
    HIGH("High", 0.20f);

    fun next(): SubtitlePosition = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(value: String): SubtitlePosition = entries.firstOrNull { it.name == value } ?: LOW
    }
}

enum class SubtitleLanguage(val label: String, val code: String) {
    AUTO("Auto", ""),
    ENGLISH("English", "en"),
    SPANISH("Spanish", "es"),
    FRENCH("French", "fr"),
    GERMAN("German", "de"),
    ITALIAN("Italian", "it"),
    PORTUGUESE("Portuguese", "pt"),
    DUTCH("Dutch", "nl"),
    JAPANESE("Japanese", "ja"),
    KOREAN("Korean", "ko"),
    CHINESE("Chinese", "zh");

    fun next(): SubtitleLanguage = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromCode(value: String): SubtitleLanguage = entries.firstOrNull { it.code == value } ?: AUTO
    }
}
