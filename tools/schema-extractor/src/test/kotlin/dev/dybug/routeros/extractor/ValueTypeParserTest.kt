package dev.dybug.routeros.extractor

import dev.dybug.routeros.schema.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueTypeParserTest {
    @Test
    fun simpleTypes() {
        assertEquals(ValueType.Bool, ValueType.parse("bool"))
        assertEquals(ValueType.Num(1, 4094), ValueType.parse("num [1 .. 4094]"))
        assertEquals(ValueType.Num(null, 32), ValueType.parse("num [ .. 32]"))
        assertEquals(ValueType.IpAddr, ValueType.parse("ipAddr"))
        assertEquals(ValueType.Time(null, null), ValueType.parse("time"))
    }

    @Test
    fun enums() {
        assertEquals(ValueType.Enum(listOf("a", "b", "c"), false), ValueType.parse("enum (a | b | c) { a:0, b:1 }"))
        assertEquals(ValueType.Enum(emptyList(), true), ValueType.parse("enum ()"))
        assertEquals(ValueType.Enum(emptyList(), true), ValueType.parse("enum"))
        assertEquals(ValueType.IfaceEnum(listOf("none")), ValueType.parse("iface_enum { none }"))
        assertEquals(ValueType.UBit(listOf("gateway", "bridge")), ValueType.parse("ubit (gateway, bridge)"))
    }

    @Test
    fun structured() {
        val negatable = ValueType.parse("super { ! , x: ipPrefix }")
        assertEquals(ValueType.Negatable(ValueType.IpPrefix), negatable)
        val multi = ValueType.parse("multi { array-id, header-field: string }")
        assertEquals(ValueType.Multi(ValueType.Str), multi)
        val composite = ValueType.parse("composite { address: ipAddr, netmask: [ num [ .. 32]] }")
        assertTrue(composite is ValueType.Composite)
        assertEquals(2, (composite as ValueType.Composite).parts.size)
    }

    @Test
    fun garbageNeverThrows() {
        for (raw in listOf("", "{", "enum (", "composite {", "num [", "))", "weird thing {a: {b: [c]}}")) {
            ValueType.parse(raw)
        }
    }
}
