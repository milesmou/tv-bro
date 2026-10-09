package com.phlox.tvwebbrowser.activity.main

import android.net.Uri
import android.widget.Toast
import com.brave.adblock.AdBlockClient
import com.brave.adblock.AdBlockClient.FilterOption
import com.brave.adblock.Utils
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.*

class AdblockModel : ActiveModel() {
    companion object {
        val TAG: String = AdblockModel::class.java.simpleName

        const val SERIALIZED_LIST_FILE = "adblock_ser.dat"
        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 30 //30 days
    }

    @Volatile private var client: AdBlockClient? = null
    val clientLoading = ObservableValue(false)
    val config = AppContext.provideConfig()

    init {
        loadAdBlockList(false)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun loadAdBlockList(forceReload: Boolean) = modelScope.launch {
        if (clientLoading.value) return@launch
        clientLoading.value = true
        val primaryUrl = config.adBlockListURL.value
        val chinaUrl = config.chinaAdBlockListURL.value
        var failures = false
        try {
            withContext(Dispatchers.IO) {
                val root = TVBro.instance.filesDir
                val primaryFile = File(root, "adblock-primary.txt")
                val chinaFile = File(root, "adblock-china.txt")
                val serialized = File(root, "adblock-combined-v1.dat")
                if (client == null) {
                    val candidate = if (serialized.exists()) serialized else File(root, SERIALIZED_LIST_FILE)
                    if (candidate.exists()) {
                        val cached = AdBlockClient()
                        if (runCatching { cached.deserialize(candidate.path) }.getOrDefault(false)) client = cached
                    }
                }
                var changed = false
                val now = System.currentTimeMillis()
                suspend fun update(file: File, sources: List<String>, china: Boolean, lastUpdate: Long) {
                    val interval = if (china) 4L * 24 * 60 * 60 * 1000 else AUTO_UPDATE_INTERVAL_MINUTES.toLong() * 60000
                    if (!forceReload && file.exists() && now - lastUpdate < interval) return
                    try {
                        val rules = com.phlox.tvwebbrowser.utils.AdblockSubscription.fetch(sources, china)
                        val atomic = android.util.AtomicFile(file)
                        val output = atomic.startWrite()
                        try {
                            output.write(rules.toByteArray(Charsets.UTF_8))
                            atomic.finishWrite(output)
                        } catch (e: Exception) { atomic.failWrite(output); throw e }
                        if (china) config.chinaAdBlockListLastUpdate = now else config.adBlockListLastUpdate = now
                        changed = true
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        failures = true
                        android.util.Log.w(TAG, "Subscription update failed; using previous cache", e)
                    }
                }
                // Chinese source is independent of the primary subscription's success.
                update(chinaFile, com.phlox.tvwebbrowser.utils.AdblockSubscription.chinaSources(chinaUrl),
                    true, config.chinaAdBlockListLastUpdate)
                update(primaryFile, com.phlox.tvwebbrowser.utils.AdblockSubscription.primarySources(primaryUrl),
                    false, config.adBlockListLastUpdate)
                val texts = listOf(primaryFile, chinaFile).mapNotNull { file ->
                    runCatching { android.util.AtomicFile(file).readFully().toString(Charsets.UTF_8) }.getOrNull()
                }
                if (texts.isEmpty()) return@withContext
                // During migration retain the old primary rules until their text can be fetched.
                if (!primaryFile.exists() && client != null && !serialized.exists()) return@withContext
                if (!changed && client != null && serialized.exists() &&
                    serialized.lastModified() >= maxOf(primaryFile.lastModified(), chinaFile.lastModified())) return@withContext
                val replacement = AdBlockClient()
                check(replacement.parse(com.phlox.tvwebbrowser.utils.AdblockSubscription.merge(texts)))
                val temporary = File(root, "adblock-combined-v1.tmp")
                try {
                    replacement.serialize(temporary.path)
                    check(temporary.isFile && temporary.length() > 0 && temporary.renameTo(serialized))
                } finally { temporary.delete() }
                client = replacement
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failures = true
            android.util.Log.w(TAG, "Keeping previous compiled ad-block rules", e)
        } finally {
            if (failures) Toast.makeText(TVBro.instance, R.string.adblock_list_load_error, Toast.LENGTH_SHORT).show()
            clientLoading.value = false
        }
    }

    fun isAd(url: Uri, type: String?, baseUri: Uri): Boolean {
        if (com.phlox.tvwebbrowser.utils.ChinaVideoAdRules.blocks(url.toString(), baseUri.toString())) return true
        val client = client ?: return false
        val baseHost = baseUri.host
        val filterOption = try {
            mapRequestToFilterOption(url, type)
        } catch (e: Exception) {
            return false
        }
        val result = try {
            baseHost != null && client.matches(url.toString(), filterOption, baseHost)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
        return result
    }

    private fun mapRequestToFilterOption(url: Uri?, type: String?): FilterOption? {
        if (type != null) {
            if (type == "image" || type.contains("image/")) {
                return FilterOption.IMAGE
            }
            if (type == "style" || type.contains("/css")) {
                return FilterOption.CSS
            }
            if (type == "script" || type.contains("javascript")) {
                return FilterOption.SCRIPT
            }
            if (type.contains("video/")) {
                return FilterOption.OBJECT
            }
        }
        if (url != null) {
            if (Utils.uriHasExtension(url, "css")) {
                return FilterOption.CSS
            }
            if (Utils.uriHasExtension(url, "js")) {
                return FilterOption.SCRIPT
            }
            if (Utils.uriHasExtension(
                    url,
                    "png",
                    "jpg",
                    "jpeg",
                    "webp",
                    "svg",
                    "gif",
                    "bmp",
                    "tiff"
                )
            ) {
                return FilterOption.IMAGE
            }
            if (Utils.uriHasExtension(url, "mp4", "mov", "avi")) {
                return FilterOption.OBJECT
            }
        }
        return FilterOption.UNKNOWN
    }
}
