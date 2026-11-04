package com.sinema.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface LoginResult {
    data class Success(val cookie: String) : LoginResult
    data object InvalidCredentials : LoginResult
    data object MissingCookie : LoginResult
    data class HttpFailure(val code: Int) : LoginResult
    data class TransportFailure(val cause: Exception) : LoginResult
}

object StashAuth {
    fun login(client: OkHttpClient, serverUrl: String, username: String, password: String): LoginResult =
        try {
            val loginClient = client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
            val body = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
            val request = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/login")
                .post(body)
                .build()
            loginClient.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> LoginResult.InvalidCredentials
                    !in listOf(200, 302, 303, 307, 308) -> LoginResult.HttpFailure(response.code)
                    else -> response.headers("Set-Cookie")
                        .firstOrNull { it.contains("session") }
                        ?.substringBefore(';')
                        ?.takeIf { it.substringAfter('=', "").isNotBlank() }
                        ?.let(LoginResult::Success)
                        ?: LoginResult.MissingCookie
                }
            }
        } catch (e: Exception) {
            LoginResult.TransportFailure(e)
        }

    fun generateApiKey(client: OkHttpClient, serverUrl: String, sessionCookie: String): String {
        val body = Gson().toJson(mapOf("query" to "mutation { generateAPIKey(input: {}) }"))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/graphql")
            .post(body)
            .addHeader("Cookie", sessionCookie)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to generate API key: ${response.code}")
            val responseBody = response.body?.string() ?: throw IOException("Empty response from server")
            val obj = try {
                Gson().fromJson(responseBody, JsonObject::class.java)
            } catch (e: Exception) {
                throw IOException("Invalid API-key response", e)
            }
            val errors = obj.getAsJsonArray("errors")
            if (errors != null && errors.size() > 0) {
                val message = errors[0].asJsonObject.get("message")?.asString ?: "GraphQL error"
                throw IOException("Failed to generate API key: $message")
            }
            val keyNode = obj.getAsJsonObject("data")?.get("generateAPIKey")
            val key = keyNode
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?: throw IOException("API key missing from server response")
            if (key.isBlank()) throw IOException("API key missing from server response")
            return key
        }
    }
}
