package dev.dybug.routeros.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosElementFactory
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosScopes
import dev.dybug.routeros.psi.RosSetStatement
import dev.dybug.routeros.psi.RosString
import dev.dybug.routeros.psi.RosTypes

/** The bare (or quoted) variable name in `:set name value`. `:set $x` and `:set ($a->k)` use variable references. */
class RosSetTargetReference(element: RosSetStatement, range: TextRange, private val target: PsiElement) :
    PsiPolyVariantReferenceBase<RosSetStatement>(element, range, true) {
    val variableName: String get() = if (target is RosString) RosPsiUtil.stringValue(target) else target.text

    fun resolveLocally(): PsiElement? {
        val declaration = RosScopes.visibleAt(element).firstOrNull { it.name == variableName } ?: return null
        return declaration.element.parent as? RosDeclaration ?: declaration.element
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        resolveLocally()?.let { return arrayOf(PsiElementResolveResult(it)) }
        return RosGlobalIndex.declarations(element.project, variableName).map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        target.replace(RosElementFactory.declarationName(element.project, newElementName))
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        fun create(statement: RosSetStatement): RosSetTargetReference? {
            val target = RosPsiUtil.declaredNameElement(statement) ?: return null
            val range = if (target is RosString) RosPsiUtil.contentRange(target) else TextRange(0, target.textLength)
            if (range.isEmpty || target.node.elementType != RosTypes.WORD && target !is RosString) return null
            return RosSetTargetReference(statement, range.shiftRight(target.startOffsetInParent), target)
        }
    }
}
