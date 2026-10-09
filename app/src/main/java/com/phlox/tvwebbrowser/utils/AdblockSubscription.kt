package com.phlox.tvwebbrowser.utils

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object AdblockSubscription {
    const val EASYLIST_OFFICIAL = "https://easylist-downloads.adblockplus.org/easylist.txt"
    const val EASYLIST_BACKUP = "https://easylist.to/easylist/easylist.txt"
    const val CHINA_OFFICIAL = "https://easylist-downloads.adblockplus.org/easylistchina.txt"
    const val CHINA_BACKUP = "https://raw.githubusercontent.com/easylist/easylistchina/master/easylistchina.txt"

    fun chinaSources(preferred: String): List<String> =
        listOf(preferred, CHINA_OFFICIAL, CHINA_BACKUP).filter { it.isNotBlank() }.distinct()

    fun primarySources(preferred: String): List<String> =
        listOf(preferred, EASYLIST_OFFICIAL, EASYLIST_BACKUP).filter { it.isNotBlank() }.distinct()

    fun fetch(sources: List<String>, china: Boolean, read: (String) -> String = ::download): String {
        var last: Exception? = null
        for (source in sources) {
            try {
                val text = read(source).removePrefix("\uFEFF")
                require(text.isNotBlank() && !text.trimStart().startsWith("<")) { "Invalid subscription" }
                if (china) require(text.lineSequence().take(30).any {
                    it.trim().equals("! Title: EasyList China", ignoreCase = true)
                }) { "Source is not EasyList China" }
                if (!china && source in listOf(EASYLIST_OFFICIAL, EASYLIST_BACKUP)) require(
                    text.lineSequence().take(30).any { it.trim().equals("! Title: EasyList", ignoreCase = true) }
                ) { "Official source is not EasyList" }
                return text
            } catch (e: Exception) { last = e }
        }
        throw IOException("All subscription sources failed", last)
    }

    fun merge(texts: List<String>): String = "[Adblock Plus 2.0]\n" + texts.joinToString("\n") { text ->
        text.lineSequence().filterNot { it.trim().startsWith("[Adblock") }.joinToString("\n")
    }

    private fun download(source: String): String {
        val url = URL(source)
        require(url.protocol == "https" || url.protocol == "http") { "Expected HTTP(S) subscription" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (result.length + count > 8 * 1024 * 1024) throw IOException("Subscription too large")
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        } finally { connection.disconnect() }
    }
}
