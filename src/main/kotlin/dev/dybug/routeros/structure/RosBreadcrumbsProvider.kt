package dev.dybug.routeros.structure

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import dev.dybug.routeros.RosLanguage
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosGlobalDecl
import dev.dybug.routeros.psi.RosLocalDecl
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosScriptCommand

class RosBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(RosLanguage)

    override fun acceptElement(element: PsiElement): Boolean = when (element) {
        is RosLocalDecl, is RosGlobalDecl -> RosPsiUtil.isFunctionDeclaration(element)
        is RosScriptCommand -> RosPsiUtil.arguments(element).any { it is RosBlock || it is RosNamedArgument && RosPsiUtil.argumentValue(it) is RosBlock }
        is RosNamedArgument -> RosPsiUtil.argumentValue(element) is RosBlock
        else -> false
    }

    override fun getElementInfo(element: PsiElement): String = when (element) {
        is RosLocalDecl -> ":local " + RosPsiUtil.declaredName(element).orEmpty()
        is RosGlobalDecl -> ":global " + RosPsiUtil.declaredName(element).orEmpty()
        is RosNamedArgument -> RosPsiUtil.argumentName(element) + "="
        is RosScriptCommand -> {
            val first = element.firstChild.text
            val second = RosPsiUtil.arguments(element).firstOrNull()?.takeIf { it !is RosNamedArgument && it !is RosBlock }?.text
            listOfNotNull(first, second?.take(30)).joinToString(" ")
        }
        else -> element.text.take(30)
    }
}
