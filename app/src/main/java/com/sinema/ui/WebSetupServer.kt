package com.sinema.ui

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class WebSetupServer private constructor(
    private val serverUrl: String,
    private val setupToken: String,
    private val onComplete: (serverUrl: String, apiKey: String) -> Unit,
    port: Int,
    bindAddress: InetAddress?
) : Closeable {
    private val socket = ServerSocket()
    private val executor = ThreadPoolExecutor(
        MAX_CLIENTS,
        MAX_CLIENTS,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUED_CLIENTS),
        ThreadFactory { task ->
            Thread(task, "sinema-web-setup-client").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy()
    )
    @Volatile private var running = true

    val localPort: Int
        get() = socket.localPort

    init {
        socket.soTimeout = ACCEPT_TIMEOUT_MS
        socket.bind(if (bindAddress == null) InetSocketAddress(port) else InetSocketAddress(bindAddress, port))
        Thread(::acceptClients, "sinema-web-setup-accept").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        if (!running) return
        running = false
        runCatching { socket.close() }
        executor.shutdownNow()
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun acceptClients() {
        while (running) {
            try {
                val client = socket.accept()
                client.soTimeout = CLIENT_READ_TIMEOUT_MS
                try {
                    if (running) executor.execute { serve(client) } else client.close()
                } catch (_: RejectedExecutionException) {
                    client.close()
                }
            } catch (_: SocketException) {
                if (running) continue else break
            } catch (_: IOException) {
                if (!running) break
            } catch (_: RuntimeException) {
                if (!running) break
            }
        }
    }

    private fun serve(client: Socket) {
        try {
            val connection = client
            val input = connection.getInputStream().buffered()
                val requestLine = readLine(input, MAX_REQUEST_LINE_BYTES)
                    ?: throw HttpRequestException(400)
                val parts = requestLine.split(' ')
                if (parts.size != 3) throw HttpRequestException(400)
                val method = parts[0]
                val path = parts[1]
                if (path != "/") throw HttpRequestException(404)

                var contentLength = 0
                var headerBytes = requestLine.toByteArray(Charsets.ISO_8859_1).size
                var headerLines = 0
                while (true) {
                    val line = readLine(input, MAX_HEADER_LINE_BYTES)
                        ?: throw HttpRequestException(400)
                    if (line.isEmpty()) break
                    headerBytes += line.toByteArray(Charsets.ISO_8859_1).size + 2
                    headerLines++
                    if (headerBytes > MAX_HEADER_BYTES || headerLines > MAX_HEADER_LINES) {
                        throw HttpRequestException(400)
                    }
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toIntOrNull()
                            ?: throw HttpRequestException(400)
                        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                            throw HttpRequestException(400)
                        }
                    }
                }

                when (method) {
                    "GET" -> writeResponse(connection, 200, "OK", setupHtml())
                    "POST" -> {
                        val body = readBody(input, contentLength).toString(Charsets.UTF_8)
                        val params = parseForm(body)
                        val token = params["setupToken"].orEmpty()
                        val validToken = MessageDigest.isEqual(
                            token.toByteArray(Charsets.UTF_8),
                            setupToken.toByteArray(Charsets.UTF_8)
                        )
                        if (!validToken) throw HttpRequestException(403)
                        val newServerUrl = params["serverUrl"]?.trim()?.trimEnd('/').orEmpty()
                        val apiKey = params["apiKey"]?.trim().orEmpty()
                        if (newServerUrl.isEmpty() || apiKey.isEmpty()) throw HttpRequestException(400)
                        onComplete(newServerUrl, apiKey)
                        writeResponse(connection, 200, "OK", SUCCESS_HTML)
                    }
                    else -> throw HttpRequestException(405)
                }
        } catch (e: HttpRequestException) {
            runCatching {
                writeResponse(client, e.status, e.reason, "<html><body><h1>${e.status}</h1></body></html>")
            }
        } catch (_: IOException) {
        } catch (_: IllegalArgumentException) {
            runCatching { writeResponse(client, 400, "Bad Request", "<html><body><h1>400</h1></body></html>") }
        } catch (e: Exception) {
            android.util.Log.w("Sinema", "Web Setup client failed", e)
        } finally {
            runCatching { client.close() }
        }
    }

    private fun setupHtml(): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Sinema Setup</title>
            <style>
                body { font-family: sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; background: #1b1b1b; color: #fff; }
                h1 { color: #2AABE0; }
                label { display: block; margin-top: 20px; margin-bottom: 8px; color: #ccc; }
                input { width: 100%; padding: 12px; font-size: 16px; background: #333; color: #fff; border: 1px solid #555; border-radius: 4px; box-sizing: border-box; }
                button { margin-top: 30px; width: 100%; padding: 14px; font-size: 18px; background: #2AABE0; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
            </style>
        </head>
        <body>
            <h1>Sinema Setup</h1>
            <form method="POST" action="/">
                <label for="serverUrl">Server URL:</label>
                <input type="text" id="serverUrl" name="serverUrl" value="${htmlEscape(serverUrl)}" required>
                <label for="apiKey">API Key:</label>
                <input type="password" id="apiKey" name="apiKey" required>
                <input type="hidden" name="setupToken" value="${htmlEscape(setupToken)}">
                <button type="submit">Save</button>
            </form>
        </body>
        </html>
    """.trimIndent()

    private fun writeResponse(connection: Socket, status: Int, reason: String, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        connection.getOutputStream().apply {
            write(headers.toByteArray(Charsets.ISO_8859_1))
            write(body)
            flush()
        }
    }

    companion object {
        private const val MAX_CLIENTS = 4
        private const val MAX_QUEUED_CLIENTS = 4
        private const val ACCEPT_TIMEOUT_MS = 1_000
        private const val CLIENT_READ_TIMEOUT_MS = 10_000
        private const val MAX_REQUEST_LINE_BYTES = 8_192
        private const val MAX_HEADER_LINE_BYTES = 8_192
        private const val MAX_HEADER_BYTES = 65_536
        private const val MAX_HEADER_LINES = 100
        private const val MAX_BODY_BYTES = 16_384
        private const val SUCCESS_HTML = "<!DOCTYPE html><html><head><title>Success</title></head>" +
            "<body><h1>Setup Complete!</h1><p>You can close this page now.</p></body></html>"

        fun start(
            serverUrl: String,
            setupToken: String,
            onComplete: (serverUrl: String, apiKey: String) -> Unit,
            port: Int = 8888,
            bindAddress: InetAddress? = null
        ): WebSetupServer = WebSetupServer(serverUrl, setupToken, onComplete, port, bindAddress)
    }
}

fun generateSetupToken(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = SecureRandom()
    return (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
}

private class HttpRequestException(val status: Int) : IOException() {
    val reason: String
        get() = when (status) {
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
}

private fun readLine(input: InputStream, maxBytes: Int): String? {
    val line = ByteArrayOutputStream()
    while (true) {
        val next = input.read()
        if (next == -1) {
            if (line.size() == 0) return null
            throw EOFException()
        }
        if (next == '\n'.code) {
            val bytes = line.toByteArray()
            val length = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
            return String(bytes, 0, length, Charsets.ISO_8859_1)
        }
        line.write(next)
        if (line.size() > maxBytes) throw HttpRequestException(400)
    }
}

private fun readBody(input: InputStream, contentLength: Int): ByteArray {
    val body = ByteArray(contentLength)
    var offset = 0
    while (offset < contentLength) {
        val read = input.read(body, offset, contentLength - offset)
        if (read < 0) throw EOFException()
        offset += read
    }
    return body
}

private fun parseForm(body: String): Map<String, String> = body.split('&')
    .filter { it.isNotEmpty() }
    .associate { entry ->
        val parts = entry.split('=', limit = 2)
        URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
    }

private fun htmlEscape(value: String): String = buildString(value.length) {
    value.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}
