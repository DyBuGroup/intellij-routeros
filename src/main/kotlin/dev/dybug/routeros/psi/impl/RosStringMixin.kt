package dev.dybug.routeros.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.RosLanguage
import dev.dybug.routeros.injection.RosStringEscaper
import dev.dybug.routeros.psi.RosInterpolation
import dev.dybug.routeros.psi.RosString
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.psi.RosVariable

abstract class RosStringMixin(node: ASTNode) : ASTWrapperPsiElement(node), RosString, PsiLanguageInjectionHost {
    /** Only plain, terminated strings: interpolation would be evaluated before the script is stored. */
    override fun isValidHost(): Boolean =
        firstChild?.node?.elementType == RosTypes.STRING_START &&
            firstChild.textLength == 1 &&
            lastChild?.node?.elementType == RosTypes.STRING_END &&
            lastChild !== firstChild &&
            PsiTreeUtil.getChildOfAnyType(this, RosVariable::class.java, RosInterpolation::class.java) == null

    override fun updateText(text: String): PsiLanguageInjectionHost {
        val file = PsiFileFactory.getInstance(project).createFileFromText("dummy.rsc", RosLanguage, ":put $text")
        val replacement = PsiTreeUtil.findChildOfType(file, RosString::class.java) ?: return this
        return replace(replacement) as PsiLanguageInjectionHost
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> = RosStringEscaper(this)
}
