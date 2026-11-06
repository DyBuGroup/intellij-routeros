package dev.dybug.routeros.references

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import dev.dybug.routeros.psi.RosDeclaration

/** Find usages of a `:global` includes the uses that resolve to its declarations in other scripts. */
class RosFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean = element is RosDeclaration && element.isGlobal

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler =
        object : FindUsagesHandler(element) {
            override fun getSecondaryElements(): Array<PsiElement> {
                if (forHighlightUsages) return PsiElement.EMPTY_ARRAY
                val name = (element as RosDeclaration).name ?: return PsiElement.EMPTY_ARRAY
                return RosGlobalIndex.declarations(element.project, name).filter { it != element }.toTypedArray()
            }
        }
}
