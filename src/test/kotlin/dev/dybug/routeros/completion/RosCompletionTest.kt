package dev.dybug.routeros.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosCompletionTest : BasePlatformTestCase() {
    private fun complete(text: String): List<String> {
        myFixture.configureByText("test.rsc", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    fun testMenuPath() {
        val items = complete("/ip fire<caret>")
        assertTrue(items.toString(), "firewall" in items || myFixture.editor.document.text.contains("firewall"))
    }

    fun testSlashPath() {
        val items = complete("/ip/<caret>")
        assertTrue(items.toString(), items.containsAll(listOf("address", "route", "firewall")))
    }

    fun testCommandWords() {
        val items = complete("/ip address <caret>")
        assertTrue(items.toString(), items.containsAll(listOf("add", "set", "print", "remove")))
    }

    fun testPropertiesOfAdd() {
        val items = complete("/ip address add <caret>")
        assertTrue(items.toString(), items.containsAll(listOf("address", "interface", "comment")))
    }

    fun testPropertyInsertsEquals() {
        myFixture.configureByText("test.rsc", "/ip address add interf<caret>")
        myFixture.completeBasic()
        myFixture.checkResult("/ip address add interface=<caret>")
    }

    fun testUsedPropertiesAreExcluded() {
        val items = complete("/ip address add interface=ether1 <caret>")
        assertFalse(items.toString(), "interface" in items)
        assertTrue("address" in items)
    }

    fun testPropertiesUseExportContext() {
        val items = complete("/ip firewall filter\nadd <caret>")
        assertTrue(items.toString(), items.containsAll(listOf("chain", "action")))
    }

    fun testEnumValues() {
        val items = complete("/ip firewall filter add action=<caret>")
        assertTrue(items.toString(), items.containsAll(listOf("accept", "drop")))
    }

    fun testBooleanValues() {
        val items = complete("/interface bridge add vlan-filtering=<caret>")
        assertTrue(items.toString(), items.containsAll(listOf("yes", "no")))
    }

    fun testInterfaceNamesFromFile() {
        val items = complete("/interface bridge\nadd name=br-lan\n/ip address\nadd interface=<caret>")
        assertTrue(items.toString(), "br-lan" in items)
    }

    fun testScriptCommands() {
        val items = complete(":fore<caret>")
        assertTrue(items.toString(), items.isEmpty() && myFixture.editor.document.text.startsWith(":foreach") || "foreach" in items)
    }

    fun testScriptCommandArguments() {
        val items = complete(":foreach i <caret>")
        assertTrue(items.toString(), items.containsAll(listOf("in", "do")))
    }

    fun testVariables() {
        val items = complete(":local count 1\n:global total 2\n:put \$<caret>")
        assertTrue(items.toString(), items.containsAll(listOf("count", "total")))
    }

    fun testLoopVariableInBlock() {
        val items = complete(":foreach k,v in=\$a do={ :put \$<caret> }")
        assertTrue(items.toString(), items.containsAll(listOf("k", "v")))
    }

    fun testLogTopics() {
        val items = complete(":log <caret>")
        assertTrue(items.toString(), items.containsAll(listOf("info", "warning", "error")))
    }

    fun testWhereProperties() {
        val items = complete("/interface print where <caret>")
        assertTrue(items.toString(), items.containsAll(listOf("name", "running")))
    }

    fun testEnumValuesInsideQuotes() {
        val items = complete("/ip firewall filter add action=\"dr<caret>\"")
        assertTrue(items.toString(), items.isEmpty() && myFixture.editor.document.text.contains("\"drop\"") || "drop" in items)
    }

    fun testPropertiesAfterPositionalArgument() {
        val items = complete("/ip address print detail <caret>")
        assertTrue(items.toString(), "without-paging" in items)
    }
}
