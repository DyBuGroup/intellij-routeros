package dev.dybug.routeros.formatting

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

@Suppress("PropertyName")
class RosCodeStyleSettings(settings: CodeStyleSettings) : CustomCodeStyleSettings("RouterOSCodeStyleSettings", settings) {
    /** Indent relative commands under the preceding menu path (`/ip address` ↵ `    add …`). Off: flat, like `/export`. */
    @JvmField
    var INDENT_SECTION_COMMANDS: Boolean = false

    /** Spaces around binary operators inside `( … )`. */
    @JvmField
    var SPACE_AROUND_OPERATORS: Boolean = true

    /** Spaces inside braces of single-line blocks: `{ :put 1 }`. */
    @JvmField
    var SPACE_WITHIN_BRACES: Boolean = true
}
