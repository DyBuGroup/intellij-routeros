package dev.dybug.routeros.schema

import junit.framework.TestCase

class RosSchemaTest : TestCase() {
    private val schema = RosSchemaLoader.loadBundled()

    fun testBundledSchemaLoads() {
        assertEquals("7.26", schema.version)
        val address = schema.node(listOf("ip", "address"))!!
        assertEquals(SchemaNode.Kind.DIRECTORY, address.kind)
        assertTrue(address.args.getValue("interface").mandatory)
        assertEquals(SchemaNode.Kind.SETTINGS, schema.node(listOf("system", "identity"))!!.kind)
    }

    fun testResolveCommand() {
        val add = schema.resolveCommand(listOf("ip", "address", "add"))
        assertEquals("add", add.verb)
        assertEquals("ip/address", add.menu!!.path)
        val fetch = schema.resolveCommand(listOf("tool", "fetch"))
        assertEquals("fetch", fetch.verb)
        assertNotNull(fetch.command)
        assertTrue(schema.argumentsOf(fetch).containsKey("url"))
        val context = schema.resolveCommand(listOf("ip", "firewall", "filter"))
        assertNull(context.verb)
        assertTrue(context.isKnown)
        val positional = schema.resolveCommand(listOf("system", "script", "run", "myscript"))
        assertFalse(positional.isKnown)
    }

    fun testArgumentsForAddIncludeEntryProperties() {
        val add = schema.resolveCommand(listOf("ip", "address", "add"))
        val args = schema.argumentsOf(add)
        assertTrue(args.keys.containsAll(listOf("address", "interface", "comment", "disabled", "copy-from")))
        val print = schema.argumentsOf(schema.resolveCommand(listOf("ip", "address", "print")))
        assertTrue(print.containsKey("detail"))
    }

    fun testAllTypesParseWithoutThrowing() {
        fun walk(node: SchemaNode) {
            (node.args.values + node.readOnly.values).forEach { it.type }
            node.children.values.forEach(::walk)
        }
        walk(schema.root)
    }

    fun testValueTypeDetector() {
        assertNull(ValueTypeDetector.problem("yes", ValueType.Bool))
        assertNotNull(ValueTypeDetector.problem("maybe", ValueType.Bool))
        assertNotNull(ValueTypeDetector.problem("5000", ValueType.Num(1, 4094)))
        assertNull(ValueTypeDetector.problem("\$x", ValueType.Num(1, 4094)))
        assertNotNull(ValueTypeDetector.problem("drop2", ValueType.Enum(listOf("accept", "drop"), false)))
        assertNull(ValueTypeDetector.problem("accept,drop", ValueType.Enum(listOf("accept", "drop"), false)))
        assertNull(ValueTypeDetector.problem("anything", ValueType.Enum(emptyList(), true)))
        assertNotNull(ValueTypeDetector.problem("300.1.1.1", ValueType.IpAddr))
        assertNull(ValueTypeDetector.problem("10.0.0.0/8", ValueType.IpPrefix))
        assertNull(ValueTypeDetector.problem("1h30m", ValueType.Time(null, null)))
    }
}
