package com.phlox.tvwebbrowser.utils

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

/** One update request; cancellation can disconnect a blocked socket from another thread. */
internal class UpdateTransfer(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    @Volatile private var cancelled = false
    @Volatile private var connection: HttpURLConnection? = null

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }

    fun download(url: URL, file: File, progress: (Long, Long) -> Unit) {
        var complete = false
        try {
            checkCancelled()
            val request = openConnection(url).apply {
                connectTimeout = 15000
                readTimeout = 10000
                useCaches = false
                setRequestProperty("Accept-Encoding", "identity")
            }
            connection = request
            checkCancelled()
            if (request.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("HTTP ${request.responseCode}: ${request.responseMessage}")
            val expected = request.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            var received = 0L
            request.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (expected >= 0 && received > expected) throw IOException("Update exceeds declared size")
                        output.write(buffer, 0, count)
                        progress(received, expected)
                    }
                }
            }
            checkCancelled()
            if (received == 0L || (expected >= 0 && received != expected))
                throw EOFException("Incomplete update: $received / $expected")
            complete = true
        } finally {
            connection?.disconnect()
            connection = null
            if (!complete) file.delete()
        }
    }

    private fun checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedIOException("Update cancelled")
    }
}
