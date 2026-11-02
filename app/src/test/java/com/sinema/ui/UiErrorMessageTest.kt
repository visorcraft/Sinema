package com.sinema.ui

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class UiErrorMessageTest {
    @Test
    fun `offline failure explains how to reconnect`() {
        assertEquals(
            "Can't reach Stash. Check the server address and your network.",
            UiErrorMessage.forException(UnknownHostException("stash.local"))
        )
    }

    @Test
    fun `authentication failure suggests signing in again`() {
        assertEquals(
            "Authentication failed. Sign in again or check the API key.",
            UiErrorMessage.forException(IOException("Server error 401"))
        )
    }

    @Test
    fun `certificate failure points to HTTPS settings`() {
        assertEquals(
            "The server certificate could not be verified. Check HTTPS and certificate settings.",
            UiErrorMessage.forException(SSLException("certificate"))
        )
    }

    @Test
    fun `missing endpoint points to the server URL`() {
        assertEquals(
            "Stash was not found at this address. Check the server URL.",
            UiErrorMessage.forException(IOException("Server error 404"))
        )
    }

    @Test
    fun `forbidden authentication failure suggests signing in again`() {
        assertEquals(
            "Authentication failed. Sign in again or check the API key.",
            UiErrorMessage.forException(IOException("Server error 403"))
        )
    }

    @Test
    fun `timeouts and connection failures use offline guidance`() {
        assertEquals(
            "Can't reach Stash. Check the server address and your network.",
            UiErrorMessage.forException(SocketTimeoutException("timeout"))
        )
        assertEquals(
            "Can't reach Stash. Check the server address and your network.",
            UiErrorMessage.forException(IOException("failed to connect"))
        )
    }

    @Test
    fun `unknown errors retain useful detail`() {
        assertEquals(
            "Something went wrong: unsupported filter",
            UiErrorMessage.forException(IllegalArgumentException("unsupported filter"))
        )
    }
}
