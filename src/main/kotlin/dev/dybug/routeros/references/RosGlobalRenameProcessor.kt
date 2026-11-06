package dev.dybug.routeros.references

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import dev.dybug.routeros.psi.RosDeclaration

/**
 * A `:global x` is the same variable in every script that declares it, so renaming one
 * declaration renames all `:global x` declarations in the project (and their references).
 */
class RosGlobalRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean = element is RosDeclaration && element.isGlobal

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val declaration = element as? RosDeclaration ?: return
        val name = declaration.name ?: return
        for (other in RosGlobalIndex.declarations(element.project, name)) {
            if (other != element) allRenames[other] = newName
        }
    }
}
