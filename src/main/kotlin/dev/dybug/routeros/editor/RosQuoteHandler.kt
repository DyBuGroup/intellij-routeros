package dev.dybug.routeros.editor

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.tree.TokenSet
import dev.dybug.routeros.psi.RosTypes

/** Strings are lexed as separate start/content/end tokens, so the default token-set handler does not fit. */
class RosQuoteHandler : QuoteHandler {
    override fun isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean =
        iterator.tokenType == RosTypes.STRING_END && iterator.end - iterator.start == 1 && offset == iterator.start

    override fun isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean =
        iterator.tokenType == RosTypes.STRING_START && iterator.end - iterator.start == 1 && offset == iterator.start

    /** The literal opened at the iterator is unclosed if no closing quote follows on the same line. */
    override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean {
        val text = editor.document.charsSequence
        var steps = 0
        try {
            iterator.advance()
            steps++
            while (!iterator.atEnd()) {
                val type = iterator.tokenType
                if (type == RosTypes.STRING_END) return false
                if (type !in CONTENT) return true
                if ((iterator.start until iterator.end).any { text[it] == '\n' || text[it] == '\r' }) return true
                iterator.advance()
                steps++
            }
            return true
        } finally {
            repeat(steps) { iterator.retreat() }
        }
    }

    override fun isInsideLiteral(iterator: HighlighterIterator): Boolean = iterator.tokenType in LITERAL_BODY

    private companion object {
        val CONTENT = TokenSet.create(
            RosTypes.STRING_TEXT,
            RosTypes.STRING_ESCAPE,
            RosTypes.STRING_INVALID_ESCAPE,
            RosTypes.DOLLAR_NAME,
        )
        val LITERAL_BODY = TokenSet.create(
            RosTypes.STRING_TEXT,
            RosTypes.STRING_ESCAPE,
            RosTypes.STRING_INVALID_ESCAPE,
        )
    }
}
