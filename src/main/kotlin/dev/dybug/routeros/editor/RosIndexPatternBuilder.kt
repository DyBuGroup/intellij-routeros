package dev.dybug.routeros.editor

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.dybug.routeros.lexer.RosLexerAdapter
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosTokenSets

class RosIndexPatternBuilder : IndexPatternBuilder {
    override fun getIndexingLexer(file: PsiFile): Lexer? = if (file is RosFile) RosLexerAdapter() else null

    override fun getCommentTokenSet(file: PsiFile): TokenSet? = if (file is RosFile) RosTokenSets.COMMENTS else null

    override fun getCommentStartDelta(tokenType: IElementType?): Int = if (tokenType != null) 1 else 0

    override fun getCommentEndDelta(tokenType: IElementType?): Int = 0
}
