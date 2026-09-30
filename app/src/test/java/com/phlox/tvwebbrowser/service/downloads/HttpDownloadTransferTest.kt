package com.phlox.tvwebbrowser.service.downloads

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpDownloadTransferTest {
    private class Response(
        private val status: Int = 200,
        private val headers: Map<String, String> = emptyMap(),
        private val body: InputStream = ByteArrayInputStream(byteArrayOf()),
    ) : HttpURLConnection(URL("https://example.test/file")) {
        var disconnected = false
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getResponseMessage() = "test"
        override fun getHeaderField(name: String) = headers[name]
        override fun getInputStream() = body
    }

    private fun withFile(block: (File) -> Unit) {
        val file = File.createTempFile("download-test", ".part")
        try { block(file) } finally { file.delete() }
    }

    @Test fun resumesTruncatedBodyWithRangeAndValidator() = withFile { file ->
        val first = Response(headers = mapOf("Content-Length" to "6", "ETag" to "\"v1\""),
            body = "abc".byteInputStream())
        val second = Response(206, mapOf("Content-Length" to "3", "Content-Range" to "bytes 3-5/6", "ETag" to "\"v1\""),
            "def".byteInputStream())
        val responses = ArrayDeque(listOf(first, second))
        HttpDownloadTransfer({ responses.removeFirst() }, 0).transfer(URL("https://example.test/file"), file,
            emptyMap(), { false }) { _, _ -> }
        assertEquals("abcdef", file.readText())
        assertEquals("bytes=3-", second.getRequestProperty("Range"))
        assertEquals("\"v1\"", second.getRequestProperty("If-Range"))
        assertTrue(first.disconnected && second.disconnected)
    }

    @Test fun replacesPartialFileWhenServerIgnoresRange() = withFile { file ->
        val responses = ArrayDeque(listOf(
            Response(headers = mapOf("Content-Length" to "6", "ETag" to "\"v1\""), body = "abc".byteInputStream()),
            Response(headers = mapOf("Content-Length" to "6", "ETag" to "\"v2\""), body = "UVWXYZ".byteInputStream()),
        ))
        HttpDownloadTransfer({ responses.removeFirst() }, 0).transfer(URL("https://example.test/file"), file,
            emptyMap(), { false }) { _, _ -> }
        assertEquals("UVWXYZ", file.readText())
    }

    @Test fun restartsWithoutValidator() = withFile { file ->
        val second = Response(headers = mapOf("Content-Length" to "6"), body = "abcdef".byteInputStream())
        val responses = ArrayDeque(listOf(
            Response(headers = mapOf("Content-Length" to "6"), body = "abc".byteInputStream()), second))
        HttpDownloadTransfer({ responses.removeFirst() }, 0).transfer(URL("https://example.test/file"), file,
            emptyMap(), { false }) { _, _ -> }
        assertNull(second.getRequestProperty("Range"))
        assertEquals("abcdef", file.readText())
    }

    @Test fun rejectsWrongRangeInsteadOfAppendingCorruptData() = withFile { file ->
        val responses = ArrayDeque(listOf(
            Response(headers = mapOf("Content-Length" to "6", "ETag" to "\"v1\""), body = "abc".byteInputStream()),
            Response(206, mapOf("Content-Length" to "3", "Content-Range" to "bytes 0-2/6"), "BAD".byteInputStream()),
            Response(headers = mapOf("Content-Length" to "6"), body = "abcdef".byteInputStream()),
        ))
        HttpDownloadTransfer({ responses.removeFirst() }, 0).transfer(URL("https://example.test/file"), file,
            emptyMap(), { false }) { _, _ -> }
        assertEquals("abcdef", file.readText())
    }

    @Test fun acceptsUnknownLengthAndReportsIndeterminateProgress() = withFile { file ->
        val sizes = mutableListOf<Long>()
        HttpDownloadTransfer({ Response(body = "data".byteInputStream()) }, 0)
            .transfer(URL("https://example.test/file"), file, emptyMap(), { false }) { _, size -> sizes.add(size) }
        assertEquals("data", file.readText())
        assertTrue(sizes.all { it == 0L })
    }

    @Test fun retriesTransientHttpFailureAndDisconnects() = withFile { file ->
        val unavailable = Response(503)
        val responses = ArrayDeque(listOf(unavailable, Response(body = "ok".byteInputStream())))
        HttpDownloadTransfer({ responses.removeFirst() }, 0).transfer(URL("https://example.test/file"), file,
            emptyMap(), { false }) { _, _ -> }
        assertEquals("ok", file.readText())
        assertTrue(unavailable.disconnected)
    }

    @Test fun doesNotRetryNotFound() = withFile { file ->
        var calls = 0
        val error = assertThrows(HttpStatusException::class.java) {
            HttpDownloadTransfer({ calls++; Response(404) }, 0).transfer(URL("https://example.test/file"), file,
                emptyMap(), { false }) { _, _ -> }
        }
        assertEquals(404, error.code)
        assertEquals(1, calls)
    }

    @Test fun rejectsPermanentlyTruncatedBodyAfterBoundedRetries() = withFile { file ->
        var calls = 0
        assertThrows(IOException::class.java) {
            HttpDownloadTransfer({ calls++; Response(headers = mapOf("Content-Length" to "6"), body = "abc".byteInputStream()) }, 0)
                .transfer(URL("https://example.test/file"), file, emptyMap(), { false }) { _, _ -> }
        }
        assertEquals(MAX_CONNECT_RETRIES, calls)
    }

    @Test fun parsesFileSizesAboveTwoGiBWithoutOverflow() = withFile { file ->
        var stop = false
        var observedSize = 0L
        assertThrows(DownloadCancelledException::class.java) {
            HttpDownloadTransfer({ Response(headers = mapOf("Content-Length" to "3221225472")) }, 0)
                .transfer(URL("https://example.test/file"), file, emptyMap(), { stop }) { _, size ->
                    observedSize = size
                    stop = true
                }
        }
        assertEquals(3221225472L, observedSize)
        assertEquals(Triple(2147483648L, 3221225471L, 3221225472L),
            HttpDownloadTransfer.parseContentRange("bytes 2147483648-3221225471/3221225472"))
    }

    @Test fun cancellationStopsBeforeOpeningConnection() = withFile { file ->
        assertThrows(DownloadCancelledException::class.java) {
            HttpDownloadTransfer({ throw AssertionError("Must not open connection") }, 0)
                .transfer(URL("https://example.test/file"), file, emptyMap(), { true }) { _, _ -> }
        }
    }
}
