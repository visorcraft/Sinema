package com.sinema.util

fun providerId(profileId: String, sceneId: String): String = "sinema:$profileId:$sceneId"

fun isSinemaProviderId(value: String?): Boolean = value?.startsWith("sinema:") == true

fun isActiveProfile(expectedProfileId: String, activeProfileId: String): Boolean =
    expectedProfileId.isNotBlank() && expectedProfileId == activeProfileId

fun isActiveServer(
    expectedProfileId: String,
    expectedServerUrl: String,
    activeProfileId: String,
    activeServerUrl: String
): Boolean = isActiveProfile(expectedProfileId, activeProfileId) &&
    expectedServerUrl.trimEnd('/') == activeServerUrl.trimEnd('/')
