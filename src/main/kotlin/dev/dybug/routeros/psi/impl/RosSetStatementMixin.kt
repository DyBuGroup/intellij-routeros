package dev.dybug.routeros.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import dev.dybug.routeros.psi.RosSetStatement
import dev.dybug.routeros.references.RosSetTargetReference

abstract class RosSetStatementMixin(node: ASTNode) : ASTWrapperPsiElement(node), RosSetStatement {
    override fun getReference(): PsiReference? = RosSetTargetReference.create(this)

    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}
