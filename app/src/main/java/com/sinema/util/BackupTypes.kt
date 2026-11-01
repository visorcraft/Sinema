package com.sinema.util

/**
 * SharedPreferences stores values with runtime types; a JSON round-trip
 * loses them. These rules restore each entry with the type the app reads
 * back, so e.g. a resume_ Long that fits in an Int still comes back Long.
 */
object BackupTypes {
    /** Keys (by prefix) the app reads with getLong(). */
    private val LONG_PREFIXES = listOf("resume_")

    enum class Kind { INT, LONG, FLOAT }

    fun numberKind(key: String, rawJsonNumber: String): Kind {
        if (LONG_PREFIXES.any { key.startsWith(it) }) return Kind.LONG
        return if (rawJsonNumber.contains('.') || rawJsonNumber.contains('e') || rawJsonNumber.contains('E')) {
            Kind.FLOAT
        } else {
            Kind.INT
        }
    }
}
