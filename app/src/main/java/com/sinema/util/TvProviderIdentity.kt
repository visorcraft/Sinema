package com.sinema.util

fun providerId(profileId: String, sceneId: String): String = "sinema:$profileId:$sceneId"

fun isSinemaProviderId(value: String?): Boolean = value?.startsWith("sinema:") == true

fun isActiveProfile(expectedProfileId: String, activeProfileId: String): Boolean =
    expectedProfileId.isNotBlank() && expectedProfileId == activeProfileId
