package com.sinema.ui

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal object UiErrorMessage {
    fun forException(error: Throwable?): String {
        val causes = generateSequence(error) { it.cause }.toList()
        val message = error?.message.orEmpty()
        return when {
            causes.any { it is SSLException } ->
                "The server certificate could not be verified. Check HTTPS and certificate settings."
            message.contains("401") || message.contains("403") ->
                "Authentication failed. Sign in again or check the API key."
            message.contains("404") ->
                "Stash was not found at this address. Check the server URL."
            causes.any { it is UnknownHostException || it is ConnectException || it is SocketTimeoutException } ||
                (error is IOException && message.contains("failed to connect", ignoreCase = true)) ->
                "Can't reach Stash. Check the server address and your network."
            message.isNotBlank() -> "Something went wrong: $message"
            else -> "Something went wrong: Unknown error"
        }
    }
}
