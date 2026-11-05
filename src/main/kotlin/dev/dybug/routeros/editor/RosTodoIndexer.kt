package dev.dybug.routeros.editor

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import com.intellij.psi.impl.cache.impl.BaseFilterLexer
import com.intellij.psi.search.UsageSearchContext
import dev.dybug.routeros.lexer.RosLexerAdapter
import dev.dybug.routeros.psi.RosTokenSets

class RosTodoIndexer : LexerBasedTodoIndexer() {
    override fun createLexer(consumer: OccurrenceConsumer): Lexer = RosFilterLexer(consumer)

    private class RosFilterLexer(consumer: OccurrenceConsumer) : BaseFilterLexer(RosLexerAdapter(), consumer) {
        override fun advance() {
            if (RosTokenSets.COMMENTS.contains(myDelegate.tokenType)) {
                scanWordsInToken(UsageSearchContext.IN_COMMENTS.toInt(), false, false)
                advanceTodoItemCountsInToken()
            }
            myDelegate.advance()
        }
    }
}
