package dev.dybug.routeros.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object RosHighlighterColors {
    @JvmField val COMMENT: TextAttributesKey = createTextAttributesKey("ROS_COMMENT", D.LINE_COMMENT)
    @JvmField val SCRIPT_COMMAND: TextAttributesKey = createTextAttributesKey("ROS_SCRIPT_COMMAND", D.KEYWORD)
    @JvmField val KEYWORD: TextAttributesKey = createTextAttributesKey("ROS_KEYWORD", D.KEYWORD)
    // WinBox terminal style: commands purple, menu paths blue, property names green, values plain.
    // Exact colours for the light and dark schemes are in resources/colorSchemes; these fallbacks
    // pick similar hues in other themes.
    @JvmField val PATH: TextAttributesKey = createTextAttributesKey("ROS_PATH", D.NUMBER)
    @JvmField val COMMAND: TextAttributesKey = createTextAttributesKey("ROS_COMMAND", D.INSTANCE_FIELD)
    @JvmField val PROPERTY: TextAttributesKey = createTextAttributesKey("ROS_PROPERTY", D.STRING)
    @JvmField val VALUE: TextAttributesKey = createTextAttributesKey("ROS_VALUE", D.IDENTIFIER)
    @JvmField val STRING: TextAttributesKey = createTextAttributesKey("ROS_STRING", D.STRING)
    @JvmField val ESCAPE: TextAttributesKey = createTextAttributesKey("ROS_ESCAPE", D.VALID_STRING_ESCAPE)
    @JvmField val INVALID_ESCAPE: TextAttributesKey = createTextAttributesKey("ROS_INVALID_ESCAPE", D.INVALID_STRING_ESCAPE)
    @JvmField val VARIABLE: TextAttributesKey = createTextAttributesKey("ROS_VARIABLE", D.CLASS_REFERENCE)
    @JvmField val NUMBER: TextAttributesKey = createTextAttributesKey("ROS_NUMBER", D.NUMBER)
    @JvmField val LITERAL: TextAttributesKey = createTextAttributesKey("ROS_LITERAL", D.NUMBER)
    @JvmField val BOOLEAN: TextAttributesKey = createTextAttributesKey("ROS_BOOLEAN", D.KEYWORD)
    @JvmField val OPERATOR: TextAttributesKey = createTextAttributesKey("ROS_OPERATOR", D.OPERATION_SIGN)
    @JvmField val BRACES: TextAttributesKey = createTextAttributesKey("ROS_BRACES", D.BRACES)
    @JvmField val BRACKETS: TextAttributesKey = createTextAttributesKey("ROS_BRACKETS", D.BRACKETS)
    @JvmField val PARENTHESES: TextAttributesKey = createTextAttributesKey("ROS_PARENTHESES", D.PARENTHESES)
    @JvmField val SEMICOLON: TextAttributesKey = createTextAttributesKey("ROS_SEMICOLON", D.SEMICOLON)
    @JvmField val COMMA: TextAttributesKey = createTextAttributesKey("ROS_COMMA", D.COMMA)
    @JvmField val SLASH: TextAttributesKey = createTextAttributesKey("ROS_SLASH", PATH)
    @JvmField val BAD_CHARACTER: TextAttributesKey = createTextAttributesKey("ROS_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
}
