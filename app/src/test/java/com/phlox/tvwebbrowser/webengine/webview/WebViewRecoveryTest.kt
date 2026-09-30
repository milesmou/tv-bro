package com.phlox.tvwebbrowser.webengine.webview

import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebSettings
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.WebTabState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowWebView
import org.robolectric.fakes.RoboWebSettings
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TVBro::class, shadows = [WebViewRecoveryTest.TrackingWebView::class])
class WebViewRecoveryTest {
    // RoboWebSettings' Safe Browsing setter is a no-op and its getter always returns false.
    @Implements(WebView::class)
    class TrackingWebView : ShadowWebView() {
        private val trackedSettings = object : RoboWebSettings() {
            private var safe = false
            override fun setSafeBrowsingEnabled(value: Boolean) { safe = value }
            override fun getSafeBrowsingEnabled() = safe
        }
        @Implementation
        public override fun getSettings(): WebSettings = trackedSettings
    }
    private fun request(mainFrame: Boolean) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse("https://example.test/page")
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    private fun view(errors: MutableList<List<Any?>>): WebViewEx {
        val callback = Proxy.newProxyInstance(WebViewEx.Callback::class.java.classLoader,
            arrayOf(WebViewEx.Callback::class.java)) { _, method, args ->
            if (method.name == "onLoadError") errors.add(args!!.toList())
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        } as WebViewEx.Callback
        return WebViewEx(RuntimeEnvironment.getApplication(), callback,
            AndroidJSInterface(WebViewWebEngine(WebTabState())))
    }

    @Test fun disablingAdsDoesNotDisableSafeBrowsing() {
        val view = view(mutableListOf())
        assertTrue(view.settings.safeBrowsingEnabled)
        view.onUpdateAdblockSetting(false)
        assertTrue(view.settings.safeBrowsingEnabled)
        view.onUpdateAdblockSetting(true)
        assertTrue(view.settings.safeBrowsingEnabled)
        view.destroy()
    }

    @Test fun onlyMainFrameFailuresOpenRecoveryPrompt() {
        val errors = mutableListOf<List<Any?>>()
        val view = view(errors)
        view.webViewClient.onReceivedHttpError(view, request(false), WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), null))
        assertTrue(errors.isEmpty())
        view.webViewClient.onReceivedHttpError(view, request(true), WebResourceResponse("text/plain", "UTF-8", 500, "Server error", emptyMap(), null))
        assertEquals(listOf("https://example.test/page", false), errors.single())
        view.destroy()
    }

    @Test fun rendererExitIsHandledEvenForBackgroundView() {
        val errors = mutableListOf<List<Any?>>()
        val view = view(errors)
        val detail = object : RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        }
        assertTrue(view.webViewClient.onRenderProcessGone(view, detail))
        assertEquals(true, errors.single()[1])
        view.destroy()
    }

    @Test fun emptySessionAfterRendererReplacementReloadsOriginalUrl() {
        val engine = WebViewWebEngine(WebTabState(url = "https://example.test/original"))
        val view = engine.getOrCreateView(RuntimeEnvironment.getApplication()) as WebViewEx
        engine.restoreState(Bundle())
        assertEquals("https://example.test/original", shadowOf(view).lastLoadedUrl)
        engine.onDetachFromWindow(completely = true, destroyTab = true)
    }
}
