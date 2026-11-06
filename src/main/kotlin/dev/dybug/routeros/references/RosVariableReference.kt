package dev.dybug.routeros.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosElementFactory
import dev.dybug.routeros.psi.RosInterpolation
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosScopes
import dev.dybug.routeros.psi.RosString
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.psi.RosVariable

/**
 * `$name` → the `:local`/`:global` declaration in scope (or a loop variable); otherwise
 * `:global` declarations of the same name in other project files. Soft: unresolved names are
 * reported by inspections, not as errors.
 */
class RosVariableReference(element: RosVariable, range: TextRange) : PsiPolyVariantReferenceBase<RosVariable>(element, range, true) {
    val variableName: String get() = RosPsiUtil.variableName(element)

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, incompleteCode)

    /** Declarations visible in the file only (no cross-file globals). */
    fun resolveLocally(): PsiElement? {
        val declaration = RosScopes.find(element, variableName) ?: return null
        return declaration.element.parent as? RosDeclaration ?: declaration.element
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val project = element.project
        // inside a string, $"quoted" would end the string: use $($\"name\") instead
        if (element.parent is RosString && !RosElementFactory.isPlainName(newElementName) &&
            newElementName.none { it == '"' || it == '\\' }
        ) {
            val escaped = newElementName.replace("$", "\\$")
            val created = RosElementFactory.file(project, ":put \"\$(\$\\\"$escaped\\\")\"")
            val interpolation = PsiTreeUtil.findChildOfType(created, RosInterpolation::class.java) ?: return element
            return element.replace(interpolation)
        }
        val first = element.firstChild
        val indexText = element.text.substring(
            if (first.node.elementType == RosTypes.DOLLAR_NAME) {
                first.textLength
            } else {
                PsiTreeUtil.getChildOfType(element, RosString::class.java)?.let { it.startOffsetInParent + it.textLength } ?: element.textLength
            },
        )
        val created = RosElementFactory.file(project, ":put \$${RosElementFactory.nameText(newElementName)}$indexText")
        val replacement = PsiTreeUtil.findChildOfType(created, RosVariable::class.java) ?: return element
        return element.replace(replacement)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        private val RESOLVER = ResolveCache.PolyVariantResolver<RosVariableReference> { ref, _ ->
            val name = ref.variableName
            if (name.isEmpty() || name.all(Char::isDigit)) return@PolyVariantResolver ResolveResult.EMPTY_ARRAY
            ref.resolveLocally()?.let { return@PolyVariantResolver arrayOf(PsiElementResolveResult(it)) }
            RosGlobalIndex.declarations(ref.element.project, name)
                .map { PsiElementResolveResult(it) }
                .toTypedArray()
        }

        fun create(variable: RosVariable): RosVariableReference? {
            val first = variable.firstChild ?: return null
            val range = when (first.node.elementType) {
                RosTypes.DOLLAR_NAME -> TextRange(1, first.textLength)
                else -> {
                    val string = PsiTreeUtil.getChildOfType(variable, RosString::class.java) ?: return null
                    RosPsiUtil.contentRange(string).shiftRight(string.startOffsetInParent)
                }
            }
            if (range.isEmpty) return null
            return RosVariableReference(variable, range)
        }
    }
}
