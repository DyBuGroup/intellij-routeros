package dev.dybug.routeros.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsTest {
    @Test
    fun parsesUpgradeAnswersAndDocsVersions() {
        assertEquals("7.25.1", Channels.parseNewest("7.25.1 1793623193\n"))
        assertEquals("7.26beta5", Channels.parseNewest("7.26beta5 1793358835"))
        assertEquals("7.26", Channels.docsVersionOf("7.26beta5"))
        assertEquals("7.24", Channels.docsVersionOf("7.24.6"))
        assertEquals("7.26", Channels.parseCurrentDocsVersion("# Manual\n\nIndex of the RouterOS 7.26 manual, in documentation menu order."))
    }

    @Test
    fun developmentAheadOfTheManualUsesTheNewestManual() {
        assertEquals("7.26", Channels.docsVersionFor("7.27beta1", "7.26"))
        assertEquals("7.24", Channels.docsVersionFor("7.24.6", "7.26"))
        assertEquals("7.26", Channels.docsVersionFor("7.26beta5", "7.26"))
        assertEquals("7.9", Channels.docsVersionFor("7.9", "7.10"))
    }

    @Test
    fun docsSources() {
        assertEquals("https://manual.mikrotik.com/llms-full.txt", Channels.docsSource("7.26", "7.26"))
        assertEquals("https://manual.mikrotik.com/docs/7.25/llms-full.txt", Channels.docsSource("7.25", "7.26"))
    }

    @Test
    fun indexJsonListsDefaultFirst() {
        val json = Channels.indexJson(listOf(Channels.Release("stable", "7.25.1"), Channels.Release("long-term", "7.24.6")))
        assertTrue(json, json.contains("\"default\": \"stable\""))
        assertTrue(json, json.contains("\"release\": \"7.24.6\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGarbage() {
        Channels.parseNewest("<html>")
    }
}
