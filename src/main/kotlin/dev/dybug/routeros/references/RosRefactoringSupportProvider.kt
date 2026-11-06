package dev.dybug.routeros.references

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import dev.dybug.routeros.psi.RosDeclaration

class RosRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = element is RosDeclaration

    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean =
        element is RosDeclaration && !element.isGlobal
}
