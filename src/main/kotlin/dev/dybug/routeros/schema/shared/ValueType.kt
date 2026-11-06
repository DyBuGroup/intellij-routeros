package dev.dybug.routeros.schema

/**
 * Value types of RouterOS properties, mirroring the type language of the CLI reference
 * (`typ="enum (a | b)"`, `num [1 .. 10]`, `composite { … }`, …).
 *
 * This file must stay free of IntelliJ Platform dependencies: the schema extractor compiles it too.
 */
sealed interface ValueType {
    data object Bool : ValueType
    data class Num(val min: Long?, val max: Long?) : ValueType
    data object Str : ValueType
    data object Switch : ValueType

    /**
     * [dynamic] = the set is open: values come from the router at runtime (`enum ()`, bare `enum`),
     * or the docs list only one or two special values next to runtime names (`enum (all) { all:0 }`).
     */
    data class Enum(val values: List<String>, val dynamic: Boolean) : ValueType
    data class IfaceEnum(val extra: List<String>) : ValueType
    data object IpAddr : ValueType
    data object Ip6Addr : ValueType
    data object IpPrefix : ValueType
    data object Ip6Prefix : ValueType
    data object IpRange : ValueType
    data object MacAddr : ValueType
    data class Time(val min: Long?, val max: Long?) : ValueType
    data object Date : ValueType
    data class Address(val flags: String) : ValueType
    data object Script : ValueType
    data object File : ValueType
    data object VarName : ValueType
    data object Expr : ValueType
    data class UBit(val names: List<String>) : ValueType
    data class Multi(val of: ValueType) : ValueType
    data class Negatable(val of: ValueType) : ValueType
    data class Optional(val of: ValueType) : ValueType
    data class Alt(val of: List<ValueType>) : ValueType
    data class Composite(val parts: List<Pair<String, ValueType>>) : ValueType

    /** Anything we do not understand. Values of unknown types are never flagged. */
    data class Unknown(val raw: String) : ValueType

    companion object {
        fun parse(raw: String): ValueType = try {
            TypeParser(raw).parseTop()
        } catch (_: RuntimeException) {
            Unknown(raw)
        }

        /** Literal values a user can pick for a type (enum values, yes/no, ubit names…). */
        fun suggestions(type: ValueType): List<String> = when (type) {
            is Bool -> listOf("yes", "no")
            is Enum -> type.values
            is IfaceEnum -> type.extra
            is UBit -> type.names
            is Multi -> suggestions(type.of)
            is Negatable -> suggestions(type.of)
            is Optional -> suggestions(type.of)
            is Alt -> type.of.flatMap(::suggestions).distinct()
            is Composite -> if (type.parts.size == 1) suggestions(type.parts.single().second) else emptyList()
            else -> emptyList()
        }

        /** Short human-readable form for completion tail text and docs. */
        fun describe(type: ValueType): String = when (type) {
            is Bool -> "yes | no"
            is Num -> "number" + range(type.min, type.max)
            is Str -> "string"
            is Switch -> "flag"
            is Enum -> when {
                type.values.isEmpty() -> "name"
                type.values.size <= 6 -> type.values.joinToString(" | ")
                else -> type.values.take(5).joinToString(" | ") + " | …"
            }
            is IfaceEnum -> "interface"
            is IpAddr -> "IPv4 address"
            is Ip6Addr -> "IPv6 address"
            is IpPrefix -> "IPv4 prefix"
            is Ip6Prefix -> "IPv6 prefix"
            is IpRange -> "IP range"
            is MacAddr -> "MAC address"
            is Time -> "time"
            is Date -> "date"
            is Address -> "address"
            is Script -> "script"
            is File -> "file"
            is VarName -> "variable name"
            is Expr -> "expression"
            is UBit -> type.names.take(4).joinToString(",") + if (type.names.size > 4) ",…" else ""
            is Multi -> "list of " + describe(type.of)
            is Negatable -> "[!]" + describe(type.of)
            is Optional -> describe(type.of)
            is Alt -> type.of.joinToString(" or ") { describe(it) }
            is Composite -> type.parts.joinToString("/") { describe(it.second) }
            is Unknown -> type.raw.substringBefore(' ').ifEmpty { "value" }
        }

        private fun range(min: Long?, max: Long?): String =
            if (min == null && max == null) "" else " [${min ?: ""}..${max ?: ""}]"
    }
}

/** Tolerant recursive-descent parser for the CLI reference type language. */
private class TypeParser(private val text: String) {
    private var pos = 0

    fun parseTop(): ValueType {
        val type = parseType()
        skipWs()
        return type
    }

    private fun parseType(): ValueType {
        skipWs()
        if (peek() == '[') {
            pos++
            val inner = parseType()
            skipTo(']')
            return ValueType.Optional(inner)
        }
        if (peek() == '!') {
            pos++
            return ValueType.Negatable(ValueType.Unknown("!"))
        }
        val start = pos
        val ident = readIdent()
        if (ident.isEmpty()) throw IllegalArgumentException("type expected at $pos in '$text'")
        return when (ident) {
            "bool" -> ValueType.Bool
            "num", "integer", "int" -> readRange().let { ValueType.Num(it.first, it.second) }
            "string", "str", "value", "text" -> ValueType.Str
            "switch" -> ValueType.Switch
            "enum" -> parseEnum()
            "iface_enum" -> ValueType.IfaceEnum(readBraceNames())
            "ipAddr" -> ValueType.IpAddr
            "ip6Addr" -> ValueType.Ip6Addr
            "ipPrefix" -> ValueType.IpPrefix
            "ip6Prefix" -> ValueType.Ip6Prefix
            "ipRange", "ip6Range" -> ValueType.IpRange
            "macAddr" -> ValueType.MacAddr
            "time" -> readRange().let { ValueType.Time(it.first, it.second) }
            "date" -> ValueType.Date
            "address" -> ValueType.Address(readParenRaw())
            "script" -> ValueType.Script
            "file" -> ValueType.File
            "varName" -> ValueType.VarName
            "expr" -> ValueType.Expr
            "ubit" -> ValueType.UBit(readParenRaw().split(',').map { it.trim() }.filter { it.isNotEmpty() })
            "multi" -> ValueType.Multi(braceMembers().firstOrNull { it.first != "array-id" }?.second ?: ValueType.Unknown(text))
            "super" -> parseSuper()
            "alt" -> ValueType.Alt(braceMembers().map { it.second })
            "composite" -> ValueType.Composite(braceMembers())
            else -> {
                skipBalancedTail()
                ValueType.Unknown(text.substring(start, pos).trim())
            }
        }
    }

    private fun parseEnum(): ValueType {
        skipWs()
        val values = if (peek() == '(') {
            readParenRaw().split('|').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        val extra = readBraceNames()
        val all = (values + extra).distinct()
        return ValueType.Enum(all, dynamic = values.size < 3)
    }

    private fun parseSuper(): ValueType {
        val members = braceMembers(allowBang = true)
        val negatable = members.any { it.first == "!" }
        val inner = members.filter { it.first != "!" }.map { it.second }
        val of = when (inner.size) {
            0 -> ValueType.Unknown(text)
            1 -> inner.single()
            else -> ValueType.Alt(inner)
        }
        return if (negatable) ValueType.Negatable(of) else of
    }

    /** Members of `{ a: T, b: U, T }` (names optional). Enum-like `{ a:0, b:1 }` maps are skipped by callers. */
    private fun braceMembers(allowBang: Boolean = false): List<Pair<String, ValueType>> {
        skipWs()
        if (peek() != '{') return emptyList()
        pos++
        val result = mutableListOf<Pair<String, ValueType>>()
        while (true) {
            skipWs()
            when (peek()) {
                null -> return result
                '}' -> {
                    pos++
                    return result
                }
                ',' -> {
                    pos++
                    continue
                }
                '!' -> {
                    pos++
                    if (allowBang) result += "!" to ValueType.Unknown("!")
                    continue
                }
            }
            val save = pos
            val name = readIdent()
            skipWs()
            if (name.isNotEmpty() && peek() == ':') {
                pos++
                result += name to parseType()
            } else {
                pos = save
                val type = parseType()
                result += (if (type is ValueType.Unknown) type.raw else "") to type
            }
            skipWs()
            if (peek() != ',' && peek() != '}') skipUntilMemberEnd()
        }
    }

    /** `{ none, auto }` after enum/iface_enum lists extra literal values; `{ a:0 }` maps keep the names. */
    private fun readBraceNames(): List<String> {
        skipWs()
        if (peek() != '{') return emptyList()
        val raw = readBalanced('{', '}')
        return raw.split(',').map { it.substringBefore(':').trim() }
            .filter { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' } }
    }

    private fun readParenRaw(): String {
        skipWs()
        if (peek() != '(') return ""
        return readBalanced('(', ')')
    }

    /** Reads `[a .. b]`; either side may be missing or non-numeric. */
    private fun readRange(): Pair<Long?, Long?> {
        skipWs()
        if (peek() != '[') return null to null
        val raw = readBalanced('[', ']')
        val parts = raw.split("..")
        if (parts.size != 2) return null to null
        return parts[0].trim().toLongOrNull() to parts[1].trim().toLongOrNull()
    }

    private fun readBalanced(open: Char, close: Char): String {
        check(peek() == open)
        var depth = 0
        val start = pos + 1
        while (pos < text.length) {
            val c = text[pos]
            if (c == open) depth++
            if (c == close) {
                depth--
                if (depth == 0) {
                    pos++
                    return text.substring(start, pos - 1)
                }
            }
            pos++
        }
        return text.substring(start)
    }

    private fun skipBalancedTail() {
        while (true) {
            skipWs()
            when (peek()) {
                '(' -> readBalanced('(', ')')
                '[' -> readBalanced('[', ']')
                '{' -> readBalanced('{', '}')
                else -> return
            }
        }
    }

    private fun skipUntilMemberEnd() {
        while (pos < text.length && text[pos] != ',' && text[pos] != '}') {
            when (text[pos]) {
                '(' -> readBalanced('(', ')')
                '[' -> readBalanced('[', ']')
                '{' -> readBalanced('{', '}')
                else -> pos++
            }
        }
    }

    private fun skipTo(c: Char) {
        skipUntil(c)
        if (peek() == c) pos++
    }

    private fun skipUntil(c: Char) {
        while (pos < text.length && text[pos] != c) pos++
    }

    private fun readIdent(): String {
        val start = pos
        while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_' || text[pos] == '-')) pos++
        return text.substring(start, pos)
    }

    private fun skipWs() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun peek(): Char? = text.getOrNull(pos)
}
