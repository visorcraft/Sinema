package com.sinema.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Null-safe JSON field access for Stash payloads: every Stash field can be
 * null in the GraphQL response, so each accessor skips JsonNull and falls
 * back to a default. Replaces the 100+ `get(x)?.takeIf { !it.isJsonNull }`
 * chains sprinkled through the parsers.
 */

fun JsonObject.optString(key: String, default: String = ""): String =
    get(key)?.takeIf { !it.isJsonNull }?.asString ?: default

fun JsonObject.optInt(key: String, default: Int = 0): Int =
    get(key)?.takeIf { !it.isJsonNull }?.asInt ?: default

fun JsonObject.optDouble(key: String, default: Double = 0.0): Double =
    get(key)?.takeIf { !it.isJsonNull }?.asDouble ?: default

fun JsonObject.optObject(key: String): JsonObject? =
    get(key)?.takeIf { !it.isJsonNull }?.asJsonObject

fun JsonObject.optArray(key: String): JsonArray? =
    get(key)?.takeIf { !it.isJsonNull }?.asJsonArray

fun JsonObject.optNullableString(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }?.asString

fun JsonObject.optNullableInt(key: String): Int? =
    get(key)?.takeIf { !it.isJsonNull }?.asInt
