package dev.dybug.routeros.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import dev.dybug.routeros.RosLanguage
import dev.dybug.routeros.lexer.RosLexerAdapter
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosTokenSets
import dev.dybug.routeros.psi.RosTypes

class RosParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = RosLexerAdapter()

    override fun createParser(project: Project?): PsiParser = RosParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = RosTokenSets.WHITESPACE

    override fun getCommentTokens(): TokenSet = RosTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = RosTokenSets.STRING_PARTS

    override fun createElement(node: ASTNode): PsiElement = RosTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = RosFile(viewProvider)

    companion object {
        @JvmField
        val FILE = IFileElementType(RosLanguage)
    }
}
