package dev.dybug.routeros.extractor

import dev.dybug.routeros.schema.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocsParserTest {
    private val nodes = DocsParser.parse(javaClass.getResource("/sample-pages.md")!!.readText()).associateBy { it.path }

    @Test
    fun parsesKindsAndPaths() {
        assertEquals(DocNode.Kind.DIRECTORY, nodes.getValue("ip/address").kind)
        assertEquals(DocNode.Kind.SETTINGS, nodes.getValue("system/identity").kind)
        assertEquals(DocNode.Kind.COMMAND, nodes.getValue("tool/fetch").kind)
        assertEquals(DocNode.Kind.COMMAND, nodes.getValue("beep").kind)
    }

    @Test
    fun parsesArgumentsFlagsAndReadOnly() {
        val address = nodes.getValue("ip/address")
        val iface = address.args.single { it.name == "interface" }
        assertEquals("iface_enum", iface.type)
        assertTrue(iface.mandatory)
        assertTrue(address.readOnly.any { it.name == "actual-interface" })
        assertTrue(address.flags.any { it.name == "X" })
        val composite = ValueType.parse(address.args.single { it.name == "address" }.type)
        assertTrue(composite.toString(), composite is ValueType.Composite)
    }

    @Test
    fun multiLineDescriptionsAndEnums() {
        val output = nodes.getValue("tool/fetch").args.single { it.name == "output" }
        assertEquals(ValueType.Enum(listOf("none", "file", "user", "user-with-headers"), false), ValueType.parse(output.type))
        assertNotNull(output.doc)
        assertTrue(output.doc!!.contains("user-with-headers"))
    }
}
