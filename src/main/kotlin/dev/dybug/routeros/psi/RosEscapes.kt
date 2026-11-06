package dev.dybug.routeros.psi

/** RouterOS string escape decoding, shared by name canonicalisation and the injection escaper. */
object RosEscapes {
    private val SIMPLE = mapOf(
        '"' to '"', '\\' to '\\', 'n' to '\n', 'r' to '\r', 't' to '\t', '$' to '$', '_' to ' ',
        'a' to '\u0007', 'b' to '\b', 'f' to '\u000C', 'v' to '\u000B',
    )

    /** Decodes escapes; invalid escapes are kept as written. Line continuations inside strings are dropped. */
    fun decode(text: String): String {
        if ('\\' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\' || i + 1 >= text.length) {
                out.append(c)
                i++
                continue
            }
            val next = text[i + 1]
            when {
                next == '\r' || next == '\n' -> i += if (next == '\r' && i + 2 < text.length && text[i + 2] == '\n') 3 else 2
                next in SIMPLE -> {
                    out.append(SIMPLE.getValue(next))
                    i += 2
                }
                isUpperHex(next) && i + 2 < text.length && isUpperHex(text[i + 2]) -> {
                    out.append(text.substring(i + 1, i + 3).toInt(16).toChar())
                    i += 3
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    fun simple(c: Char): Char? = SIMPLE[c]

    fun isUpperHex(c: Char) = c in '0'..'9' || c in 'A'..'F'
}
