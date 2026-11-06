package dev.dybug.routeros.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosString

/** Lets the platform (fragment editor, rename in injected code…) replace string content safely. */
class RosStringManipulator : AbstractElementManipulator<RosString>() {
    override fun handleContentChange(element: RosString, range: TextRange, newContent: String): RosString {
        val text = element.text
        val updated = text.substring(0, range.startOffset) + escape(newContent) + text.substring(range.endOffset)
        return (element as RosStringMixin).updateText(updated) as RosString
    }

    override fun getRangeInElement(element: RosString): TextRange = RosPsiUtil.contentRange(element)

    companion object {
        /** Encodes text so that it reads back unchanged from a RouterOS double-quoted string. */
        fun escape(content: String): String = buildString {
            for (c in content) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c.code < 0x20) append("\\").append("%02X".format(c.code)) else append(c)
                }
            }
        }
    }
}
