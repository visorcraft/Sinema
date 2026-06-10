package com.sinema.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sinema.model.ServerProfile

object ProfileCodec {
    private val gson = Gson()
    private val listType = object : TypeToken<List<ServerProfile>>() {}.type

    fun toJson(profiles: List<ServerProfile>): String = gson.toJson(profiles, listType)

    fun fromJson(json: String): List<ServerProfile> {
        return try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
