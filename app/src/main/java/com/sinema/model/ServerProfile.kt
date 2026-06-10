package com.sinema.model

import java.util.UUID

data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val serverUrl: String = "",
    val apiKey: String = "",
    val sessionCookie: String = "",
    val authMode: String = "apikey",
    val stashUsername: String = "",
    val stashPassword: String = ""
)
