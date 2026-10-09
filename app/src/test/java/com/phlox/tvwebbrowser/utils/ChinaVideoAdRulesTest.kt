package com.phlox.tvwebbrowser.utils

import org.junit.Assert.*
import org.junit.Test

class ChinaVideoAdRulesTest {
    @Test fun blocksOnlyKnownAdRequestsOnMatchingSites() {
        val examples = listOf(
            "https://v.qq.com/x/cover/test" to "https://l.qq.com/lview?x=1",
            "https://www.iqiyi.com/v_test.html" to "https://info.vip.iqiyi.com/promotion/test",
            "https://v.youku.com/v_show/id_test.html" to "https://ad.youku.com/vp?x=1",
            "https://www.mgtv.com/b/test" to "https://da.mgtv.com/test",
            "https://www.bilibili.com/video/BVtest" to "https://api.bilibili.com/x/ad/test",
            "https://www.douyu.com/1" to "https://static.douyucdn.cn/adxdsp/test",
            "https://www.huya.com/1" to "https://a.msstatic.com/huya/main/img/mc-recom_test.png"
        )
        examples.forEach { (page, ad) ->
            assertTrue(ad, ChinaVideoAdRules.blocks(ad, page))
            assertFalse(ad, ChinaVideoAdRules.blocks(ad, "https://example.com"))
            val adHost = java.net.URI(ad).host
            val pageHost = java.net.URI(page).host
            assertFalse(ad, ChinaVideoAdRules.blocks(ad.replace(adHost, "$adHost.evil.test"), page))
            assertFalse(ad, ChinaVideoAdRules.blocks(ad, page.replace(pageHost, "$pageHost.evil.test")))
        }
    }

    @Test fun leavesPlaybackLoginAndUnrelatedSitesUntouched() {
        listOf("https://api.bilibili.com/x/player/playurl", "https://passport.bilibili.com/login",
            "https://upos-sz-mirrorcos.bilivideo.com/video.m4s", "https://api.bilibili.com/x/adventure/").forEach {
            assertFalse(ChinaVideoAdRules.blocks(it, "https://www.bilibili.com/video/test"))
        }
        assertFalse(ChinaVideoAdRules.blocks("https://l.qq.com/lviewer", "https://v.qq.com"))
        assertFalse(ChinaVideoAdRules.blocks("https://api.bilibili.com.evil.test/x/ad/a", "https://www.bilibili.com"))
        assertEquals("", ChinaVideoAdRules.css("https://bilibili.com.evil.test"))
        assertEquals("", ChinaVideoAdRules.css("about:blank"))
        assertTrue(ChinaVideoAdRules.css("https://www.bilibili.com").contains(".ad-report"))
        assertFalse(ChinaVideoAdRules.css("https://www.iqiyi.com").contains("videoBuyContainer"))
    }
}
