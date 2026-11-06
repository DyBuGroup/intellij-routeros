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
        assertEquals(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, keys["service"]!!.fallbackAttributeKey)
        assertEquals(DefaultLanguageHighlighterColors.INSTANCE_FIELD, keys["port"]!!.fallbackAttributeKey)
        assertEquals(DefaultLanguageHighlighterColors.NUMBER, keys["8080"]!!.fallbackAttributeKey)
        assertEquals(DefaultLanguageHighlighterColors.CLASS_REFERENCE, keys["\$lanAddress"]!!.fallbackAttributeKey)
    }
}
