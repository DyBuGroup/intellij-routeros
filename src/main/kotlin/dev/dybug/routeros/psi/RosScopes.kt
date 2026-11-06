package dev.dybug.routeros.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

/**
 * Variable scoping rules:
 * - `:local` is visible after its declaration in the enclosing block and nested blocks;
 * - loop/handler variables (`:foreach x`, `:for i`, `:onerror e`) are visible in the command's blocks;
 * - `:global` is visible in the whole file (and project-wide, see the stub index).
 */
object RosScopes {
    enum class Kind { LOCAL, GLOBAL, LOOP, FUNCTION_LOCAL, FUNCTION_GLOBAL }

    data class Declaration(val name: String, val element: PsiElement, val kind: Kind)

    /** Declarations visible at [place], innermost first; each name appears once. */
    fun visibleAt(place: PsiElement): List<Declaration> {
        val result = linkedMapOf<String, Declaration>()
        var child: PsiElement = place
        var scope: PsiElement? = place.parent
        while (scope != null && scope !is PsiFile) {
            if (scope is RosBlock || scope is RosBracketCommand) {
                collectPreceding(scope, child, result)
            }
            loopVariable(scope)?.let { loop ->
                loopVariableNames(scope).forEach { result.putIfAbsent(it, Declaration(it, loop.element, Kind.LOOP)) }
            }
            child = scope
            scope = scope.parent
        }
        if (scope != null) {
            collectPreceding(scope, child, result)
            for (decl in PsiTreeUtil.findChildrenOfType(scope, RosGlobalDecl::class.java)) {
                declaration(decl)?.let { result.putIfAbsent(it.name, it) }
            }
        }
        return result.values.toList()
    }

    private fun collectPreceding(scope: PsiElement, until: PsiElement, into: MutableMap<String, Declaration>) {
        var sibling = until.prevSibling
        while (sibling != null) {
            if (sibling is RosLocalDecl || sibling is RosGlobalDecl) {
                declaration(sibling)?.let { into.putIfAbsent(it.name, it) }
            }
            sibling = sibling.prevSibling
        }
    }

    fun declaration(decl: PsiElement): Declaration? {
        val name = RosPsiUtil.declaredName(decl) ?: return null
        val function = RosPsiUtil.isFunctionDeclaration(decl)
        val kind = when (decl) {
            is RosLocalDecl -> if (function) Kind.FUNCTION_LOCAL else Kind.LOCAL
            is RosGlobalDecl -> if (function) Kind.FUNCTION_GLOBAL else Kind.GLOBAL
            else -> return null
        }
        return Declaration(name, RosPsiUtil.declaredNameElement(decl) ?: decl, kind)
    }

    /** `:foreach x in=…`, `:foreach k,v in=…`, `:for i from=…`, `:onerror e in=…` declare a variable. */
    fun loopVariable(statement: PsiElement): Declaration? {
        if (statement !is RosScriptCommand) return null
        val name = statement.firstChild?.text ?: return null
        if (name != ":foreach" && name != ":for" && name != ":onerror") return null
        val word = RosPsiUtil.arguments(statement).firstOrNull()?.takeIf { it.node.elementType == RosTypes.WORD } ?: return null
        val variable = word.text.substringAfter(',').ifEmpty { word.text }
        return Declaration(variable, word, Kind.LOOP)
    }

    /** For `:foreach k,v`, both names are declared. */
    fun loopVariableNames(statement: PsiElement): List<String> {
        val decl = loopVariable(statement) ?: return emptyList()
        return decl.element.text.split(',').filter { it.isNotEmpty() }
    }
}
