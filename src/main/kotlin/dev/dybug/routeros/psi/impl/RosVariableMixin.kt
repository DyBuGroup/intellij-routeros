package dev.dybug.routeros.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import dev.dybug.routeros.psi.RosVariable
import dev.dybug.routeros.references.RosVariableReference

abstract class RosVariableMixin(node: ASTNode) : ASTWrapperPsiElement(node), RosVariable {
    override fun getReference(): PsiReference? = RosVariableReference.create(this)

    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}
