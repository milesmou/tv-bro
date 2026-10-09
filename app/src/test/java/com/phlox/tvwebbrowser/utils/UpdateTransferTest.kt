package com.phlox.tvwebbrowser.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class UpdateTransferTest {
    private val url = URL("https://example.test/update.apk")
    private open inner class Connection(private val bytes: ByteArray, private val length: Int) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = 200
        override fun getHeaderField(name: String) = if (name == "Content-Length") length.toString() else null
        override fun getInputStream() = bytes.inputStream()
    }

    @Test fun verifiesLengthAndDeletesPartialFiles() {
        val file = File.createTempFile("update-test", ".apk")
        try {
            val connection = Connection(byteArrayOf(1, 2), 2)
            UpdateTransfer({ connection }).download(url, file) { _, _ -> }
            assertArrayEquals(byteArrayOf(1, 2), file.readBytes())
            assertEquals(15000, connection.connectTimeout)
            assertEquals(10000, connection.readTimeout)
            assertTrue(connection.disconnected)
            try {
                UpdateTransfer({ Connection(byteArrayOf(1), 2) }).download(url, file) { _, _ -> }
                fail("Expected incomplete transfer")
            } catch (_: IOException) { }
            assertFalse(file.exists())
        } finally { file.delete() }
    }

    @Test fun cancellationDisconnectsBlockedReadAndCleansFile() {
        val reading = CountDownLatch(1)
        val released = CountDownLatch(1)
        val connection = object : HttpURLConnection(url) {
            override fun connect() {}
            override fun usingProxy() = false
            override fun disconnect() { released.countDown() }
            override fun getResponseCode() = 200
            override fun getInputStream() = object : java.io.InputStream() {
                override fun read(): Int {
                    reading.countDown()
                    if (!released.await(3, TimeUnit.SECONDS)) throw IOException("Timeout")
                    throw IOException("Disconnected")
                }
            }
        }
        val file = File.createTempFile("update-cancel", ".apk")
        val transfer = UpdateTransfer({ connection })
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<Boolean> {
                try { transfer.download(url, file) { _, _ -> }; false } catch (_: IOException) { true }
            }
            assertTrue(reading.await(2, TimeUnit.SECONDS))
            transfer.cancel()
            assertTrue(result.get(2, TimeUnit.SECONDS))
            assertFalse(file.exists())
        } finally { transfer.cancel(); executor.shutdownNow(); file.delete() }
    }
}
