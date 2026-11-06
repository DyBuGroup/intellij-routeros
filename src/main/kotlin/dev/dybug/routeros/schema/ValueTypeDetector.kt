package dev.dybug.routeros.schema

/**
 * Heuristic checks of literal argument values against schema types. RouterOS is lenient and
 * types in the docs are approximate, so callers report mismatches as weak warnings only and
 * every check errs on the side of accepting the value.
 */
object ValueTypeDetector {
    private val NUMBER = Regex("""-?(0x[0-9A-Fa-f]+|[0-9]+)""")
    private val IPV4 = Regex("""(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])(\.(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])){3}""")
    private val IPV4_PREFIX = Regex("""${IPV4.pattern}(/([0-9]|[12][0-9]|3[0-2]))?""")
    private val MAC = Regex("""[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){5}""")
    private val TIME = Regex("""([0-9]+(w|d|h|m|s|ms|us|ns))+|[0-9]+(:[0-9]{1,2}){1,2}(\.[0-9]+)?|[0-9]+d[0-9]{1,2}:[0-9]{2}:[0-9]{2}|[0-9]+|none|never|unlimited|infinity""")
    private val BOOL = setOf("yes", "no", "true", "false")

    /** Returns a short problem description, or null when the value is acceptable or cannot be judged. */
    fun problem(value: String, type: ValueType): String? {
        if (value.isEmpty() || value.startsWith("$") || value.startsWith("[") || value.startsWith("(")) return null
        return when (type) {
            is ValueType.Bool -> if (value in BOOL) null else "expected yes or no"
            is ValueType.Num -> numberProblem(value, type)
            is ValueType.Enum -> enumProblem(value, type)
            is ValueType.IpAddr -> if (!IPV4.matches(value) && looksLikeIp(value)) "expected an IPv4 address" else null
            is ValueType.IpPrefix -> if (IPV4_PREFIX.matches(value)) null else "expected an IPv4 prefix".takeIf { looksLikeBrokenIp(value) }
            is ValueType.MacAddr -> if (MAC.matches(value)) null else "expected a MAC address".takeIf { value.contains(':') }
            is ValueType.Time -> if (TIME.matches(value)) null else "expected a time such as 10s or 00:01:00".takeIf { value.first().isDigit() }
            is ValueType.Negatable -> problem(value.removePrefix("!"), type.of)
            else -> null
        }
    }

    private fun numberProblem(value: String, type: ValueType.Num): String? {
        if (!NUMBER.matches(value)) return if (value.first().isDigit() && value.all { it.isDigit() || it == '.' }) "expected a number" else null
        val number = value.removePrefix("-").let { if (it.startsWith("0x")) it.drop(2).toLongOrNull(16) else it.toLongOrNull() } ?: return null
        val signed = if (value.startsWith("-")) -number else number
        if (type.min != null && signed < type.min) return "value is below the minimum ${type.min}"
        if (type.max != null && signed > type.max) return "value is above the maximum ${type.max}"
        return null
    }

    private fun enumProblem(value: String, type: ValueType.Enum): String? {
        if (type.dynamic || type.values.isEmpty()) return null
        val parts = value.split(',')
        val bad = parts.map { it.removePrefix("!") }.firstOrNull { it.isNotEmpty() && it !in type.values } ?: return null
        // numeric codes are frequently accepted where names are documented (protocol=6, port numbers…)
        if (bad.first().isDigit()) return null
        return "unknown value '$bad'"
    }

    private fun looksLikeIp(value: String) = value.count { it == '.' } == 3 && value.all { it.isDigit() || it == '.' }

    private fun looksLikeBrokenIp(value: String) =
        value.first().isDigit() && value.all { it.isDigit() || it == '.' || it == '/' } && value.count { it == '.' } == 3
}
