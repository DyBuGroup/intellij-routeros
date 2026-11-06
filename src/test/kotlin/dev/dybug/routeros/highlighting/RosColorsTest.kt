package dev.dybug.routeros.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.lexer.RosLexerAdapter

class RosColorsTest : BasePlatformTestCase() {
    /** `/ip service set www address="$lanAddress.0/24" port=8080`: each part gets its own colour. */
    fun testServiceExample() {
        val text = "/ip service\nset www address=\"\$lanAddress.0/24\" port=8080\n"
        val highlighter = RosSyntaxHighlighter()
        val lexer = RosLexerAdapter()
        lexer.start(text)
        val keys = mutableMapOf<String, TextAttributesKey?>()
        while (lexer.tokenType != null) {
            keys.putIfAbsent(lexer.tokenText, highlighter.getTokenHighlights(lexer.tokenType!!).firstOrNull())
            lexer.advance()
        }
        assertEquals(RosHighlighterColors.PATH, keys["service"])
        assertEquals(RosHighlighterColors.PROPERTY, keys["port"])
        assertEquals(RosHighlighterColors.VALUE, keys["8080"])
        assertEquals(DefaultLanguageHighlighterColors.CLASS_REFERENCE, keys["\$lanAddress"]!!.fallbackAttributeKey)
    }

    /** WinBox style in the bundled light and dark schemes: command purple, path blue, property green, value plain. */
    fun testSchemeColours() {
        val manager = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance()
        var checked = 0
        for ((schemeName, expected) in mapOf(
            "Default" to listOf(0x871094, 0x0033b3, 0x067d17),
            "Darcula" to listOf(0xc77dbb, 0x56a8f5, 0x6aab73),
        )) {
            val scheme = manager.getScheme(schemeName) ?: continue
            checked++
            val actual = listOf(RosHighlighterColors.COMMAND, RosHighlighterColors.PATH, RosHighlighterColors.PROPERTY)
                .map { scheme.getAttributes(it)?.foregroundColor?.rgb?.and(0xffffff) }
            assertEquals(schemeName, expected, actual)
        }
        assertTrue("no bundled colour scheme found", checked > 0)
    }
}
