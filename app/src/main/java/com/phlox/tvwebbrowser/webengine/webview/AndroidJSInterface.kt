package com.phlox.tvwebbrowser.webengine.webview

import android.net.http.SslError
import android.webkit.JavascriptInterface
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.utils.DownloadUtils
import org.json.JSONArray
import org.json.JSONObject


class AndroidJSInterface(private val webEngine: WebViewWebEngine) {
    private val blobTransfers = java.util.concurrent.ConcurrentHashMap<String, com.phlox.tvwebbrowser.utils.ChunkedBlobInputStream>()

    @JavascriptInterface
    fun beginBlobDownload(fileName: String?, url: String, mimetype: String, size: Long): String {
        val callback = webEngine.callback ?: return ""
        if (size < 0 || !url.startsWith("blob:") || blobTransfers.size >= 2) return ""
        val token = java.util.UUID.randomUUID().toString()
        val stream = com.phlox.tvwebbrowser.utils.ChunkedBlobInputStream(size) { blobTransfers.remove(token) }
        blobTransfers[token] = stream
        callback.getActivity().runOnUiThread {
            if (webEngine.callback !== callback || stream.isAborted()) {
                stream.close()
                return@runOnUiThread
            }
            callback.onDownloadRequested(url, "", fileName?.takeIf { it.isNotBlank() }
                ?: DownloadUtils.guessFileName(url, null, mimetype), "", mimetype,
                Download.OperationAfterDownload.NOP, null, stream, size)
        }
        return token
    }

    @JavascriptInterface
    fun appendBlobChunk(token: String, data: String): Int {
        val stream = blobTransfers[token] ?: return -1
        return try {
            if (data.length > 87384) throw java.io.IOException("Blob chunk too large")
            if (stream.offer(android.util.Base64.decode(data, android.util.Base64.DEFAULT))) 1 else 0
        } catch (e: Exception) {
            stream.abort()
            blobTransfers.remove(token)
            -1
        }
    }

    @JavascriptInterface
    fun finishBlobDownload(token: String) { blobTransfers.remove(token)?.finish() }

    @JavascriptInterface
    fun abortBlobDownload(token: String) { blobTransfers.remove(token)?.abort() }

    fun abortBlobDownloads() {
        blobTransfers.values.forEach { it.abort() }
        blobTransfers.clear()
    }

    @JavascriptInterface
    fun currentUrl(): String {
        if (!webEngine.tab.url.startsWith(WebViewEx.INTERNAL_SCHEME)) return ""
        return webEngine.tab.url
    }

    @JavascriptInterface
    fun reloadWithSslTrust() {
        val callback = webEngine.callback ?: return
        if ((webEngine.getView() as WebViewEx).currentOriginalUrl?.scheme != "file") return
        callback.getActivity().runOnUiThread {
            val webview = webEngine.getView() as? WebViewEx ?: return@runOnUiThread
            webview.trustSsl = true
            webEngine.tab.url.apply { webEngine.loadUrl(this) }
        }
    }

    @JavascriptInterface
    fun getStringByName(name: String): String {
        val ctx = TVBro.instance
        //val resId = ctx.resources.getIdentifier(name, "string", ctx.packageName)
        //return ctx.getString(resId)
        when (name) {
            "connection_isnt_secure" -> return ctx.getString(R.string.connection_isnt_secure)
            "hostname" -> return ctx.getString(R.string.hostname)
            "err_desk" -> return ctx.getString(R.string.err_desk)
            "details" -> return ctx.getString(R.string.details)
            "back_to_safety" -> return ctx.getString(R.string.back_to_safety)
            "go_im_aware" -> return ctx.getString(R.string.go_im_aware)
            else -> return ""
        }
    }



    @JavascriptInterface
    fun openHomeAction(action: String) {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { callback.onHomePageAction(action) }
    }

    @JavascriptInterface
    fun setSearchEngine(engine: String, customSearchEngineURL: String) {
        if (!isHomePage()) return
        AppContext.provideConfig().searchEngineURL.value = customSearchEngineURL
    }

    @JavascriptInterface
    fun onEditBookmark(index: Int) {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { callback.onEditHomePageBookmarkSelected(index) }
    }

    @JavascriptInterface
    fun onHomePageLoaded() {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread {
            val cfg = AppContext.provideConfig()
            val jsArr = JSONArray()
            for (item in callback.getHomePageLinks()) {
                jsArr.put(item.toJsonObj())
            }
            webEngine.evaluateJavascript("renderLinks(${JSONObject.quote(cfg.homePageLinksMode.name)}, $jsArr)")
            webEngine.evaluateJavascript(
                "applySearchEngine(${JSONObject.quote(cfg.guessSearchEngineName())}, ${JSONObject.quote(cfg.searchEngineURL.value)})")
        }
    }

    @JavascriptInterface
    fun lastSSLError(getDetails: Boolean): String {
        val lastSSLError = (webEngine.getView() as? WebViewEx)?.lastSSLError ?: return "unknown"
        return if (getDetails) {
            lastSSLError.toString()
        } else {
            when (lastSSLError.primaryError) {
                SslError.SSL_EXPIRED -> TVBro.instance.getString(R.string.ssl_expired)
                SslError.SSL_IDMISMATCH -> TVBro.instance.getString(R.string.ssl_idmismatch)
                SslError.SSL_DATE_INVALID -> TVBro.instance.getString(R.string.ssl_date_invalid)
                SslError.SSL_INVALID -> TVBro.instance.getString(R.string.ssl_invalid)
                else -> "unknown"
            }
        }
    }

    @JavascriptInterface
    fun markBookmarkRecommendationAsUseful(bookmarkOrder: Int) {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { callback.markBookmarkRecommendationAsUseful(bookmarkOrder) }
    }

    private fun isHomePage(): Boolean {
        return webEngine.tab.url == Config.HOME_PAGE_URL || webEngine.tab.url == Config.HOME_URL_ALIAS
    }
}
