package dev.dybug.routeros.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.dybug.routeros.lexer.RosLexerAdapter
import dev.dybug.routeros.psi.RosTokenSets
import dev.dybug.routeros.psi.RosTypes

class RosSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = RosLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(ATTRIBUTES[tokenType])

    private companion object {
        val ATTRIBUTES: Map<IElementType, TextAttributesKey> = buildMap {
            put(RosTypes.COMMENT, RosHighlighterColors.COMMENT)
            RosTokenSets.SCRIPT_COMMANDS.types.forEach { put(it, RosHighlighterColors.SCRIPT_COMMAND) }
            RosTokenSets.KEYWORDS.types.forEach { put(it, RosHighlighterColors.KEYWORD) }
            RosTokenSets.OPERATORS.types.forEach { put(it, RosHighlighterColors.OPERATOR) }
            put(RosTypes.PATH_WORD, RosHighlighterColors.PATH)
            put(RosTypes.SLASH, RosHighlighterColors.SLASH)
            put(RosTypes.ARG_NAME, RosHighlighterColors.PROPERTY)
            put(RosTypes.WORD, RosHighlighterColors.VALUE)
            put(RosTypes.STRING_START, RosHighlighterColors.STRING)
            put(RosTypes.STRING_END, RosHighlighterColors.STRING)
            put(RosTypes.STRING_TEXT, RosHighlighterColors.STRING)
            put(RosTypes.STRING_ESCAPE, RosHighlighterColors.ESCAPE)
            put(RosTypes.STRING_INVALID_ESCAPE, RosHighlighterColors.INVALID_ESCAPE)
            put(RosTypes.VARIABLE, RosHighlighterColors.VARIABLE)
            put(RosTypes.DOLLAR, RosHighlighterColors.VARIABLE)
            put(RosTypes.NUMBER, RosHighlighterColors.NUMBER)
            put(RosTypes.LITERAL, RosHighlighterColors.LITERAL)
            put(RosTypes.BOOLEAN, RosHighlighterColors.BOOLEAN)
            put(RosTypes.LBRACE, RosHighlighterColors.BRACES)
            put(RosTypes.RBRACE, RosHighlighterColors.BRACES)
            put(RosTypes.LBRACKET, RosHighlighterColors.BRACKETS)
            put(RosTypes.RBRACKET, RosHighlighterColors.BRACKETS)
            put(RosTypes.LPAREN, RosHighlighterColors.PARENTHESES)
            put(RosTypes.RPAREN, RosHighlighterColors.PARENTHESES)
            put(RosTypes.SEMICOLON, RosHighlighterColors.SEMICOLON)
            put(RosTypes.COMMA, RosHighlighterColors.COMMA)
            put(TokenType.BAD_CHARACTER, RosHighlighterColors.BAD_CHARACTER)
        }
    }
}
