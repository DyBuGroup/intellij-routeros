package dev.dybug.routeros.editor

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import dev.dybug.routeros.psi.RosTypes

/** Spell-check comments and string text only; menu paths, properties and values are not English words. */
class RosSpellcheckingStrategy : SpellcheckingStrategy() {
    override fun getTokenizer(element: PsiElement): Tokenizer<*> = when {
        element is PsiComment -> TEXT_TOKENIZER
        element.elementType == RosTypes.STRING_TEXT -> TEXT_TOKENIZER
        else -> EMPTY_TOKENIZER
    }
}
