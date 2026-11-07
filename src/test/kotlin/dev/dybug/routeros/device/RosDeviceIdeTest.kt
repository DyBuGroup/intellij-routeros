package dev.dybug.routeros.device

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.device.run.RosConsoleFilter
import dev.dybug.routeros.device.validate.RosRemoteValidator
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.schema.RosSchemaLoader

class RosDeviceIdeTest : BasePlatformTestCase() {
    fun testExportStyleDetection() {
        val export = myFixture.configureByText("e.rsc", "# header\n/ip address\nadd address=1.2.3.4/24 interface=ether1\n") as RosFile
        assertTrue(RosRemoteValidator.isExportStyle(export))
        val script = myFixture.configureByText("s.rsc", ":local a 1\n/ip address print\n") as RosFile
        assertFalse(RosRemoteValidator.isExportStyle(script))
    }

    fun testConsoleFilterLinksToLine() {
        val file = myFixture.configureByText("s.rsc", ":put 1\n:foo\n").virtualFile
        val line = "bad command name foo (line 2 column 1)"
        val result = RosConsoleFilter(project, file).applyFilter(line, line.length)!!
        assertEquals("line 2 column 1", line.substring(result.resultItems.single().highlightStartOffset, result.resultItems.single().highlightEndOffset))
    }

    fun testSyncedSchemaMergesWithBundled() {
        val device = JsonObject().apply {
            addProperty("version", "7.25")
            add(
                "nodes",
                JsonObject().apply {
                    add("ip/address", JsonObject().apply { add("args", JsonArray().apply { add(JsonObject().apply { addProperty("n", "device-only") }) }) })
                    add("vendor/thing", JsonObject().apply { addProperty("kind", "dir") })
                },
            )
        }
        val schema = RosSchemaLoader.loadBundled(merge = device)
        assertEquals("7.25", schema.version)
        val address = schema.node(listOf("ip", "address"))!!
        assertTrue(address.args.containsKey("device-only"))
        assertTrue(address.args.getValue("interface").mandatory)
        assertNotNull(schema.node(listOf("vendor", "thing")))
    }

    fun testSettingsRoundTrip() {
        val settings = RosDeviceSettings.getInstance(project)
        settings.update(listOf(RouterProfile().apply { name = "lab"; url = "https://10.0.0.1" }), "lab")
        assertEquals("https://10.0.0.1", settings.profile(null)?.url)
        assertNull(settings.profile("missing"))
        assertEquals("lab", settings.profile("")?.name)
    }

    fun testSchemaVersionFollowsRouterOrSelection() {
        val settings = RosDeviceSettings.getInstance(project)
        val service = dev.dybug.routeros.schema.RosProjectSchemaService.getInstance(project)
        val bundled = dev.dybug.routeros.schema.RosSchemaLoader.bundledVersions
        val oldest = bundled.minWith(dev.dybug.routeros.schema.RosProjectSchemaService::compareVersions)
        val newest = bundled.maxWith(dev.dybug.routeros.schema.RosProjectSchemaService::compareVersions)
        fun routerRuns(version: String) {
            settings.update(listOf(RouterProfile().apply { name = "lab"; lastSeenVersion = version }), "lab")
            service.refreshNow()
        }
        try {
            routerRuns("$oldest.6")
            assertEquals(oldest, service.effectiveVersion)
            assertEquals(oldest, service.schema?.version)
            routerRuns("9.99")
            assertEquals(newest, service.effectiveVersion)
            routerRuns("6.49.10")
            assertEquals(dev.dybug.routeros.schema.RosSchemaLoader.defaultVersion, service.effectiveVersion)
            settings.state.schemaVersion = newest
            routerRuns("$oldest.6")
            assertEquals(newest, service.effectiveVersion)
        } finally {
            settings.state.schemaVersion = RosDeviceSettings.AUTO_SCHEMA
            settings.update(emptyList(), "")
            service.refreshNow()
        }
    }
}
