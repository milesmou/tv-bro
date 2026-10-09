package com.phlox.tvwebbrowser.utils

import android.app.Activity
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.Html
import android.webkit.MimeTypeMap
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.phlox.tvwebbrowser.R
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(val currentVersionCode: Int) {
    var versionCheckResult: UpdateCheckResult? = null

    class ChangelogEntry(val versionCode: Int, val versionName: String, val changes: String)
    class UpdateCheckResult(val latestVersionCode: Int, val latestVersionName: String, val channel:String,
                            val url: String, val changelog: ArrayList<ChangelogEntry>, val availableChannels: Array<String>)
    interface DialogCallback {
        fun download()
        fun later()
        fun settings()
    }

    fun check(urlOfVersionFile: String, channelsToCheck: Array<String>) {
        val urlConnection = (URL(urlOfVersionFile).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 10000
        }
        try {
            val content = urlConnection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(content)
            val channelsJson = json.getJSONArray("channels")
            var latestVersionCode = 0
            var latestVersionName = ""
            var url = ""
            var latestVersionChannelName = ""
            val availableChannels = ArrayList<String>()
            val currentCPUArch = Build.SUPPORTED_ABIS[0]
            for (i in 0 until channelsJson.length()) {
                val channelJson = channelsJson.getJSONObject(i)
                availableChannels.add(channelJson.getString("name"))
                if (channelsToCheck.contains(channelJson.getString("name"))) {
                    val minAPI = if (channelJson.has("minAPI")) channelJson.getInt("minAPI") else 21
                    if (latestVersionCode < channelJson.getInt("latestVersionCode") &&
                            minAPI <= Build.VERSION.SDK_INT) {
                        latestVersionCode = channelJson.getInt("latestVersionCode")
                        latestVersionName = channelJson.getString("latestVersionName")
                        url = channelJson.getString("url")
                        if (channelJson.has("urls")) {
                            val urls = channelJson.getJSONArray("urls")
                            for (j in 0 until urls.length()) {
                                val fileUrl = urls.getString(j)
                                if (fileUrl.endsWith("$currentCPUArch.apk")) {
                                    url = fileUrl
                                    break
                                }
                            }
                        }
                        latestVersionChannelName = channelJson.getString("name")
                    }
                }
            }
            val changelogJson = json.getJSONArray("changelog")
            val changelog = ArrayList<ChangelogEntry>()
            for (i in 0 until changelogJson.length()) {
                val versionChangesJson = changelogJson.getJSONObject(i)
                changelog.add(ChangelogEntry(versionChangesJson.getInt("versionCode"),
                        versionChangesJson.getString("versionName"),
                        versionChangesJson.getString("changes")))
            }
            versionCheckResult = UpdateCheckResult(latestVersionCode, latestVersionName, latestVersionChannelName, url, changelog, availableChannels.toTypedArray())
        } finally {
            urlConnection.disconnect()
        }
    }

    fun showUpdateDialog(context: Activity, callback: DialogCallback) {
        val version = versionCheckResult ?: return
        if (version.latestVersionCode < currentVersionCode) {
            throw IllegalStateException("Version less than current")
        }
        var message = ""
        for (changelogEntry in version.changelog) {
            if (changelogEntry.versionCode > currentVersionCode) {
                message += "<b>${changelogEntry.versionName}</b><br>" +
                        changelogEntry.changes.replace("\n", "<br>") + "<br>"
            }
        }
        val textView = TextView(context)
        textView.text = Html.fromHtml(message)
        val padding = Utils.D2P(context, 25f).toInt()
        textView.setPadding(padding, padding, padding, padding)
        AlertDialog.Builder(context)
                .setTitle(R.string.new_version_dialog_title)
                .setView(textView)
                .setPositiveButton(R.string.download) { _, _ -> callback.download() }
                .setNegativeButton(R.string.later) { _, _ -> callback.later() }
                .setNeutralButton(R.string.settings) { _, _ -> callback.settings() }
                .show()
    }

    suspend fun downloadUpdate(context: Activity, modelScope: CoroutineScope) {
        val update = versionCheckResult ?: return
        val dialog = ProgressDialog(context)
        dialog.setCancelable(true)
        dialog.setMessage(context.getString(R.string.downloading_file))
        dialog.isIndeterminate = false
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        dialog.show()
        val downloadedFile = Utils.createTempFile(context, UPDATE_APK_FILE_NAME)
        val transfer = UpdateTransfer()
        var cancelledByUser = false
        var nextUpdate = 0L
        try {
            coroutineScope {
                val job = async(Dispatchers.IO) {
                    transfer.download(URL(update.url), downloadedFile) { received, expected ->
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now >= nextUpdate) {
                            nextUpdate = now + 100
                            context.runOnUiThread {
                                if (dialog.isShowing) {
                                    dialog.isIndeterminate = expected <= 0
                                    if (expected > 0) dialog.progress = (received * 100 / expected).toInt()
                                }
                            }
                        }
                    }
                    val archive = context.packageManager.getPackageArchiveInfo(downloadedFile.path, 0)
                    if (archive?.packageName != context.packageName)
                        throw java.io.IOException("Invalid update package")
                }
                dialog.setOnCancelListener {
                    cancelledByUser = true
                    job.cancel()
                    modelScope.launch(Dispatchers.IO) { transfer.cancel() }
                }
                try {
                    job.await()
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { transfer.cancel() }
                }
            }
        } catch (e: CancellationException) {
            downloadedFile.delete()
            if (!cancelledByUser) throw e
            return
        } catch (e: Exception) {
            downloadedFile.delete()
            if (!cancelledByUser) Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
            return
        } finally {
            dialog.dismiss()
        }
        if (cancelledByUser || context.isFinishing || context.isDestroyed) return

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(downloadedFile.extension)
        val apkURI = FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".provider", downloadedFile)

        val install = Intent(Intent.ACTION_INSTALL_PACKAGE)
        install.setDataAndType(apkURI, mimeType)
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(install)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }

    fun hasUpdate(): Boolean {
        val versionCheckResult = this.versionCheckResult ?: return false
        return versionCheckResult.latestVersionCode > currentVersionCode
    }

    companion object {
        private const val UPDATE_APK_FILE_NAME = "update.apk"
        fun clearTempFilesIfAny(context: Context) {
            var tempFile = File(context.cacheDir, UPDATE_APK_FILE_NAME)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            tempFile = File(context.externalCacheDir, UPDATE_APK_FILE_NAME)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}