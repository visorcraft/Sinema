package com.sinema.util

import com.sinema.model.ServerProfile

data class SessionRefreshResult(
    val profiles: List<ServerProfile>,
    val updateActiveCookie: Boolean
)

fun routeSessionRefresh(
    profiles: List<ServerProfile>,
    activeProfileId: String,
    originatingProfileId: String,
    cookie: String
): SessionRefreshResult = SessionRefreshResult(
    profiles = profiles.map { profile ->
        if (profile.id == originatingProfileId) profile.copy(sessionCookie = cookie) else profile
    },
    updateActiveCookie = activeProfileId.isNotBlank() && activeProfileId == originatingProfileId
)
