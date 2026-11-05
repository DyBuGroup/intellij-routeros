package dev.dybug.routeros.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

/** Decodes RouterOS string escapes so that injected script text is what the router would store. */
class RosStringEscaper<T : PsiLanguageInjectionHost>(host: T) : LiteralTextEscaper<T>(host) {
    private var offsets: IntArray = IntArray(0)

    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
        val text = rangeInsideHost.substring(myHost.text)
        val map = IntArray(text.length + 1)
        val start = outChars.length
        var ok = true
        var i = 0
        while (i < text.length) {
            map[outChars.length - start] = i
            val c = text[i]
            if (c != '\\') {
                outChars.append(c)
                i++
                continue
            }
            if (i + 1 >= text.length) {
                ok = false
                break
            }
            val next = text[i + 1]
            when {
                next == '\r' || next == '\n' -> {
                    i += if (next == '\r' && i + 2 < text.length && text[i + 2] == '\n') 3 else 2
                    continue
                }
                next in SIMPLE -> outChars.append(SIMPLE.getValue(next))
                isUpperHex(next) && i + 2 < text.length && isUpperHex(text[i + 2]) -> {
                    outChars.append(text.substring(i + 1, i + 3).toInt(16).toChar())
                    i += 3
                    continue
                }
                else -> {
                    ok = false
                    break
                }
            }
            i += 2
        }
        map[outChars.length - start] = i
        offsets = map.copyOf(outChars.length - start + 1)
        return ok
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
        if (offsetInDecoded < 0 || offsetInDecoded >= offsets.size) return -1
        return (rangeInsideHost.startOffset + offsets[offsetInDecoded]).coerceAtMost(rangeInsideHost.endOffset)
    }

    override fun isOneLine(): Boolean = false

    private fun isUpperHex(c: Char) = c in '0'..'9' || c in 'A'..'F'

    private companion object {
        val SIMPLE = mapOf(
            '"' to '"', '\\' to '\\', 'n' to '\n', 'r' to '\r', 't' to '\t', '$' to '$', '_' to ' ',
            'a' to '\u0007', 'b' to '\b', 'f' to '\u000C', 'v' to '\u000B',
        )
    }
}
