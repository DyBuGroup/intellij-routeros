package dev.dybug.routeros.references

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import dev.dybug.routeros.lexer.RosLexerAdapter
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosTokenSets
import dev.dybug.routeros.psi.RosTypes

class RosFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        RosLexerAdapter(),
        TokenSet.create(RosTypes.WORD, RosTypes.DOLLAR_NAME, RosTypes.PATH_WORD, RosTypes.ARG_NAME),
        RosTokenSets.COMMENTS,
        TokenSet.create(RosTypes.STRING_TEXT),
    )

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is RosDeclaration

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when {
        element is RosDeclaration && element.isFunction -> "function"
        element is RosDeclaration && element.isGlobal -> "global variable"
        else -> "local variable"
    }

    override fun getDescriptiveName(element: PsiElement): String = (element as? RosDeclaration)?.name ?: element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
}
