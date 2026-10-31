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
    val stashPassword: String = "",
    /**
     * Opt-in: disable TLS certificate verification for this server only
     * (self-hosted Stash behind a self-signed certificate). Defaults to
     * false; stored profiles deserializing without the field stay strict.
     */
    val allowSelfSigned: Boolean = false
)
