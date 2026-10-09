package com.phlox.tvwebbrowser.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AdblockSubscriptionTest {
    @Test fun primaryMirrorIsFirstAndFallsBackWithoutReplacingCustomRules() {
        val mirror = "https://mirror.test/easylist.txt"
        val sources = AdblockSubscription.primarySources(mirror)
        assertEquals(mirror, sources.first())
        val custom = "||custom-ad.example^"
        assertEquals(custom, AdblockSubscription.fetch(sources, false) { custom })
        val easylist = "[Adblock Plus 2.0]\n! Title: EasyList\n||ads.example^"
        val calls = mutableListOf<String>()
        assertEquals(easylist, AdblockSubscription.fetch(sources, false) { source ->
            calls.add(source)
            when (source) {
                mirror -> throw IOException("Mirror unavailable")
                AdblockSubscription.EASYLIST_OFFICIAL -> "! Title: Wrong list\n||wrong.example^"
                else -> easylist
            }
        })
        assertEquals(sources, calls)
        assertEquals(2, AdblockSubscription.primarySources(AdblockSubscription.EASYLIST_OFFICIAL).size)
    }

    private val china = "[Adblock Plus 2.0]\n! Title: EasyList China\n||ads.example^\n@@||allowed.example^"

    @Test fun mirrorFailureAndWrongListFallBackToOfficial() {
        val calls = mutableListOf<String>()
        val sources = AdblockSubscription.chinaSources("https://mirror.test/china.txt")
        val result = AdblockSubscription.fetch(sources, true) { url ->
            calls.add(url)
            if (url == sources.first()) "<html>Login</html>" else china
        }
        assertEquals(china, result)
        assertEquals(listOf(sources[0], AdblockSubscription.CHINA_OFFICIAL), calls)
        val fromBackup = AdblockSubscription.fetch(sources, true) { url ->
            when(url) {
                sources[0] -> throw IOException("Offline")
                AdblockSubscription.CHINA_OFFICIAL -> "! Title: Wrong list\n||ads.example^"
                else -> china
            }
        }
        assertEquals(china, fromBackup)
    }

    @Test fun defaultsAreDeduplicatedAndExceptionsSurviveMerge() {
        assertEquals(2, AdblockSubscription.chinaSources(AdblockSubscription.CHINA_OFFICIAL).size)
        val merged = AdblockSubscription.merge(listOf("[Adblock Plus 2.0]\n||ads.example^", china))
        assertEquals(1, merged.lineSequence().count { it.startsWith("[Adblock") })
        assertTrue(merged.contains("@@||allowed.example^"))
        assertTrue(merged.contains("! Title: EasyList China"))
    }

    @Test(expected = IOException::class) fun allFailedSourcesDoNotReturnErrorPageAsRules() {
        AdblockSubscription.fetch(listOf("https://mirror.test"), true) { "<html>Unavailable</html>" }
    }
}
