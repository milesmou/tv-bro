package com.phlox.tvwebbrowser.service.downloads

import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.Download
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TVBro::class)
class DownloadTaskTest {
    @Test fun publishesOnlyCompleteDownloadsAndCleansFailedOrCancelledTransfers() {
        val directory = File(RuntimeEnvironment.getApplication().cacheDir, "download-test").apply { mkdirs() }
        fun info(name: String) = Download().apply {
            url = "https://example.test/file"
            filename = name
            filepath = File(directory, name).absolutePath
        }
        var done = 0
        var errors = 0
        val callback = object : DownloadTask.Callback {
            override fun onProgress(task: DownloadTask) {}
            override fun onError(task: DownloadTask, responseCode: Int, responseMessage: String) { errors++ }
            override fun onDone(task: DownloadTask) { done++ }
        }
        val successful = info("complete")
        StreamDownloadTask(successful, "complete body".byteInputStream(), callback).run()
        assertEquals("complete body", File(successful.filepath).readText())
        assertEquals(13L, successful.bytesReceived)
        assertEquals(successful.bytesReceived, successful.size)
        assertEquals(1, done)

        var failedClosed = false
        val brokenStream = object : InputStream() {
            var delivered = false
            override fun read(): Int = throw IOException("disconnected")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (delivered) throw IOException("disconnected")
                delivered = true
                buffer[offset] = 42
                return 1
            }
            override fun close() { failedClosed = true }
        }
        val failed = info("failed")
        StreamDownloadTask(failed, brokenStream, callback).run()
        assertEquals(Download.BROKEN_MARK, failed.size)
        assertFalse(File(failed.filepath).exists())
        assertTrue(failedClosed)
        assertEquals(1, errors)

        var cancelledClosed = false
        val cancelled = info("cancelled").apply { this.cancelled = true }
        StreamDownloadTask(cancelled, object : InputStream() {
            override fun read(): Int = throw AssertionError("Cancelled stream must not be consumed")
            override fun close() { cancelledClosed = true }
        }, callback).run()
        assertEquals(Download.CANCELLED_MARK, cancelled.size)
        assertFalse(File(cancelled.filepath).exists())
        assertTrue(cancelledClosed)
        assertEquals(2, done)

        val blob = info("blob").apply { mimeType = "text/plain" }
        BlobDownloadTask(blob, "data:text/plain;base64,aGVsbG8=", callback).run()
        assertEquals("hello", File(blob.filepath).readText())
        assertEquals(5L, blob.size)
        assertEquals(3, done)
        assertTrue(RuntimeEnvironment.getApplication().cacheDir.listFiles()!!.none { it.name.startsWith("download-") && it.extension == "part" })
        directory.deleteRecursively()
    }
}
