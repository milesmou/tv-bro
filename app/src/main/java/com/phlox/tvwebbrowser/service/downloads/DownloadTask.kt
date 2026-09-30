package com.phlox.tvwebbrowser.service.downloads

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.provider.MediaStore
import android.util.Base64
import android.util.Base64InputStream
import android.util.Log
import android.webkit.CookieManager
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.singleton.AppDatabase
import com.phlox.tvwebbrowser.utils.DownloadUtils
import java.io.*
import java.net.URL

const val MAX_CONNECT_RETRIES = 5

interface DownloadTask {
    var downloadInfo: Download
    interface Callback {
        fun onProgress(task: DownloadTask)
        fun onError(task: DownloadTask, responseCode: Int, responseMessage: String)
        fun onDone(task: DownloadTask)
    }
}

class FileDownloadTask(override var downloadInfo: Download, private val userAgent: String?, val callback: DownloadTask.Callback) : Runnable, DownloadTask {
    companion object { val TAG = FileDownloadTask::class.java.simpleName }

    override fun run() = runDownload(this, callback) { staging ->
        val url = URL(downloadInfo.url)
        val headers = mutableMapOf<String, String>()
        userAgent?.let { headers["User-Agent"] = it }
        downloadInfo.mimeType?.let { headers["Accept"] = it }
        downloadInfo.referer?.let { headers["Referer"] = it }
        CookieManager.getInstance().getCookie(url.toString())?.let { headers["Cookie"] = it }
        HttpDownloadTransfer().transfer(url, staging, headers, { downloadInfo.cancelled }, onHeaders = { connection ->
            // Older Android already selected a collision-free destination filename in the service.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                connection.getHeaderField("Content-Disposition")?.let {
                    downloadInfo.filename = File(DownloadUtils.guessFileName(downloadInfo.url, it,
                        connection.getHeaderField("Content-Type"))).name
                }
            }
        }) { received, size ->
            downloadInfo.bytesReceived = received
            downloadInfo.size = size
            callback.onProgress(this)
        }
    }
}

class BlobDownloadTask(override var downloadInfo: Download, val blobBase64Data: String, val callback: DownloadTask.Callback) : Runnable, DownloadTask {
    override fun run() = runDownload(this, callback) { staging ->
        val start = if (blobBase64Data.startsWith("data:")) blobBase64Data.indexOf(',') + 1 else 0
        // Decode incrementally; avoid duplicating the full blob in byte arrays.
        val encoded = object : InputStream() {
            private var index = start
            override fun read(): Int = if (index < blobBase64Data.length) blobBase64Data[index++].code else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (index >= blobBase64Data.length) return -1
                val count = minOf(length, blobBase64Data.length - index)
                repeat(count) { buffer[offset + it] = blobBase64Data[index++].code.toByte() }
                return count
            }
        }
        Base64InputStream(encoded, Base64.DEFAULT).use { input ->
            staging.outputStream().use { copyDownload(input, it, this, callback) }
        }
    }
}

class StreamDownloadTask(override var downloadInfo: Download, val stream: InputStream, val callback: DownloadTask.Callback) : Runnable, DownloadTask {
    override fun run() {
        try {
            runDownload(this, callback) { staging ->
                stream.use { input -> staging.outputStream().use { copyDownload(input, it, this, callback) } }
            }
        } finally {
            // Cancellation before staging still owns and must close the incoming stream.
            runCatching { stream.close() }
        }
    }
}

private fun copyDownload(input: InputStream, output: OutputStream, task: DownloadTask,
    callback: DownloadTask.Callback, reportProgress: Boolean = true) {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        checkCancelled { task.downloadInfo.cancelled }
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        total += count
        if (reportProgress) {
            task.downloadInfo.bytesReceived = total
            callback.onProgress(task)
        }
    }
}

private fun runDownload(task: DownloadTask, callback: DownloadTask.Callback, transfer: (File) -> Unit) {
    val info = task.downloadInfo
    var staging: File? = null
    var destinationCreated = false
    var failure: Exception? = null
    try {
        info.id = AppDatabase.db.downloadDao().insert(info)
        checkCancelled { info.cancelled }
        staging = File.createTempFile("download-", ".part", TVBro.instance.cacheDir)
        transfer(staging)
        checkCancelled { info.cancelled }
        val completedSize = staging.length()
        prepareDownloadOutput(info) { destinationCreated = true }.use { output ->
            staging.inputStream().use { copyDownload(it, output, task, callback, reportProgress = false) }
        }
        checkCancelled { info.cancelled }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            if (TVBro.instance.contentResolver.update(Uri.parse(info.filepath), values, null, null) != 1)
                throw IOException("Unable to publish download")
        }
        info.size = completedSize
        info.bytesReceived = completedSize
    } catch (e: Exception) {
        failure = e
        info.size = if (info.cancelled || e is DownloadCancelledException) Download.CANCELLED_MARK else Download.BROKEN_MARK
        if (destinationCreated) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    TVBro.instance.contentResolver.delete(Uri.parse(info.filepath), null, null)
                } else File(info.filepath).delete()
            } catch (cleanupError: Exception) {
                Log.e(FileDownloadTask.TAG, "Unable to remove incomplete download", cleanupError)
            }
        }
        info.bytesReceived = 0
    } finally {
        staging?.delete()
    }
    // Notify only after streams are closed and destination cleanup/publication is complete.
    if (failure != null && info.size != Download.CANCELLED_MARK) {
        callback.onError(task, (failure as? HttpStatusException)?.code ?: 0, failure.toString())
    } else callback.onDone(task)
}

private fun prepareDownloadOutput(info: Download, onCreated: () -> Unit): OutputStream {
    val resolver = TVBro.instance.contentResolver
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, info.filename)
            put(MediaStore.Downloads.MIME_TYPE, info.mimeType ?: "application/octet-stream")
            put(MediaStore.Downloads.DOWNLOAD_URI, info.url)
            put(MediaStore.Downloads.REFERER_URI, info.referer)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("Unable to create download")
        info.filepath = uri.toString()
        onCreated()
        AppDatabase.db.downloadDao().update(info)
        val descriptor = resolver.openFileDescriptor(uri, "w") ?: throw IOException("Unable to open download")
        AutoCloseOutputStream(descriptor)
    } else {
        FileOutputStream(info.filepath).also { onCreated() }
    }
}
