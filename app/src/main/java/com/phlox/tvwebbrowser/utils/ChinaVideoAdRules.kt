package com.phlox.tvwebbrowser.utils

import java.net.URI
import java.util.Locale

/** Conservative, site-scoped subset of EasyList China. Rule data: CC BY-SA 3.0.
 * Attribution, source and modifications: assets/adblock/NOTICE.txt.
 * No player, media CDN, authentication, or subscription/paywall selectors.
 */
object ChinaVideoAdRules {
    const val VERSION = "2026-10-09"
    private data class RequestRule(val site: String, val host: String, val pathPrefix: String)
    private val requests = listOf(
        RequestRule("v.qq.com", "l.qq.com", "/lview"),
        RequestRule("iqiyi.com", "info.vip.iqiyi.com", "/promotion/"),
        RequestRule("youku.com", "ad.youku.com", "/vp"),
        RequestRule("mgtv.com", "da.mgtv.com", "/"),
        RequestRule("bilibili.com", "api.bilibili.com", "/x/ad/"),
        RequestRule("douyu.com", "douyucdn.cn", "/adxdsp/"),
        RequestRule("huya.com", "msstatic.com", "/huya/main/img/mc-recom_")
    )
    private val selectors = mapOf(
        "v.qq.com" to "a[href*='.renrendai.com'],a[href*='.vip.com']",
        "iqiyi.com" to "#bottom_banner_ad_bk,#widget-jingdongAd,.adBanner980",
        "youku.com" to ".src_adwrap,div[class*='adDrawer']",
        "mgtv.com" to ".ad-fixed-bar",
        "bilibili.com" to ".adcard-content,.ad-report,.bili-dyn-ads",
        "huya.com" to "#huya-ab,#huya-ab-fixed,.huya-ab,.end-ab-banner",
        "douyu.com" to ".ScreenBannerAd,.IconCardAdBoundsBox,.adcontainer"
    )

    private fun host(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "http" && uri.scheme != "https") null
        else uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
    }.getOrNull()

    private fun matchesHost(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    fun blocks(requestUrl: String, pageUrl: String): Boolean {
        val pageHost = host(pageUrl) ?: return false
        val requestHost = host(requestUrl) ?: return false
        val path = runCatching { URI(requestUrl).rawPath }.getOrNull() ?: return false
        return requests.any { rule ->
            matchesHost(pageHost, rule.site) && matchesHost(requestHost, rule.host) &&
                if (rule.pathPrefix.endsWith('/') || rule.pathPrefix.endsWith('_')) path.startsWith(rule.pathPrefix)
                else path == rule.pathPrefix
        }
    }

    fun css(pageUrl: String): String {
        val pageHost = host(pageUrl) ?: return ""
        val selector = selectors.entries.firstOrNull { matchesHost(pageHost, it.key) }?.value ?: return ""
        return "$selector { display: none !important; }"
    }
}
