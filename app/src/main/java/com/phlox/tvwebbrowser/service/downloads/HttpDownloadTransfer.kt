package com.phlox.tvwebbrowser.service.downloads

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

internal class DownloadCancelledException : InterruptedIOException("Download cancelled")
internal class HttpStatusException(val code: Int, message: String) : IOException(message)

/** Writes to a private staging file. Only a verified complete transfer is published. */
internal class HttpDownloadTransfer(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val retryDelayMillis: Long = 1000,
) {
    fun transfer(
        url: URL,
        file: File,
        headers: Map<String, String>,
        cancelled: () -> Boolean,
        onHeaders: (HttpURLConnection) -> Unit = {},
        onProgress: (Long, Long) -> Unit,
    ) {
        var validator: String? = null
        var expectedTotal = -1L
        var lastError: IOException? = null
        RandomAccessFile(file, "rw").use { output ->
            repeat(MAX_CONNECT_RETRIES) { attempt ->
                checkCancelled(cancelled)
                if (attempt > 0) {
                    var remaining = retryDelayMillis * attempt
                    while (remaining > 0) {
                        checkCancelled(cancelled)
                        val delay = minOf(remaining, 100)
                        Thread.sleep(delay)
                        remaining -= delay
                    }
                }
                var connection: HttpURLConnection? = null
                try {
                    // Without an entity validator, restart instead of joining different versions.
                    if (validator == null) output.setLength(0)
                    var offset = output.length()
                    connection = openConnection(url).apply {
                        connectTimeout = 20000
                        readTimeout = 10000
                        useCaches = false
                        headers.forEach { (key, value) -> setRequestProperty(key, value) }
                        setRequestProperty("Accept-Encoding", "identity")
                        if (offset > 0) {
                            setRequestProperty("Range", "bytes=$offset-")
                            setRequestProperty("If-Range", validator)
                        }
                    }
                    val code = connection.responseCode
                    if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                        val error = HttpStatusException(code, "HTTP $code: ${connection.responseMessage}")
                        if (code !in setOf(408, 429, 500, 502, 503, 504)) throw error
                        lastError = error
                        return@repeat
                    }
                    val responseValidator = connection.getHeaderField("ETag")?.takeUnless { it.startsWith("W/") }
                        ?: connection.getHeaderField("Last-Modified")
                    val length = connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 } ?: -1L
                    if (code == HttpURLConnection.HTTP_PARTIAL) {
                        val range = parseContentRange(connection.getHeaderField("Content-Range"))
                        if (range == null || range.first != offset ||
                            (validator != null && responseValidator != null && validator != responseValidator) ||
                            (expectedTotal >= 0 && expectedTotal != range.third) ||
                            (length >= 0 && length != range.second - range.first + 1)) {
                            output.setLength(0)
                            validator = null
                            expectedTotal = -1
                            throw IOException("Invalid partial download response")
                        }
                        expectedTotal = range.third
                    } else {
                        // If Range was ignored or the entity changed, replace the partial file.
                        offset = 0
                        output.setLength(0)
                        expectedTotal = length
                    }
                    validator = responseValidator
                    onHeaders(connection)
                    onProgress(offset, expectedTotal.coerceAtLeast(0))
                    output.seek(offset)
                    var received = offset
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            checkCancelled(cancelled)
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (expectedTotal >= 0 && received + count > expectedTotal) {
                                throw IOException("Download exceeds declared size")
                            }
                            output.write(buffer, 0, count)
                            received += count
                            onProgress(received, expectedTotal.coerceAtLeast(0))
                        }
                    }
                    checkCancelled(cancelled)
                    if (expectedTotal >= 0 && received != expectedTotal) throw EOFException("Download interrupted")
                    return
                } catch (e: DownloadCancelledException) {
                    throw e
                } catch (e: HttpStatusException) {
                    throw e
                } catch (e: IOException) {
                    lastError = e
                } finally {
                    connection?.disconnect()
                }
            }
        }
        throw lastError ?: IOException("Download failed")
    }

    companion object {
        fun parseContentRange(value: String?): Triple<Long, Long, Long>? {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value ?: "") ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val end = match.groupValues[2].toLongOrNull() ?: return null
            val total = match.groupValues[3].toLongOrNull() ?: return null
            return if (start <= end && end < total) Triple(start, end, total) else null
        }
    }
}

internal fun checkCancelled(cancelled: () -> Boolean) {
    if (cancelled() || Thread.currentThread().isInterrupted) throw DownloadCancelledException()
}
