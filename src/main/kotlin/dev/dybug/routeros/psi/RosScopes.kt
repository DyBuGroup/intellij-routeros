package dev.dybug.routeros.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * Variable scoping rules:
 * - `:local` is visible after its declaration in the enclosing block and nested blocks;
 * - loop/handler variables (`:foreach x`, `:for i`, `:onerror e`) are visible in the command's `do={…}` block;
 * - `:global` is visible in the whole file (and project-wide, see the global index).
 *
 * Declarations are collected once per file modification into [ScopeIndex], so each lookup costs
 * O(nesting depth × declarations with that name) instead of re-walking the file.
 */
object RosScopes {
    enum class Kind { LOCAL, GLOBAL, LOOP, FUNCTION_LOCAL, FUNCTION_GLOBAL }

    data class Declaration(val name: String, val element: PsiElement, val kind: Kind)

    private class Entry(val offset: Int, val declaration: Declaration)

    private class ScopeIndex(
        /** scope (block, bracket substitution or file) → declarations that are its direct statements, by offset */
        val byScope: Map<PsiElement, List<Entry>>,
        val globals: List<Declaration>,
    )

    private fun index(file: PsiFile): ScopeIndex = CachedValuesManager.getCachedValue(file) {
        val byScope = HashMap<PsiElement, MutableList<Entry>>()
        val globals = mutableListOf<Declaration>()
        for (decl in PsiTreeUtil.findChildrenOfAnyType(file, RosLocalDecl::class.java, RosGlobalDecl::class.java)) {
            val declaration = declaration(decl) ?: continue
            byScope.getOrPut(decl.parent) { mutableListOf() } += Entry(decl.textRange.startOffset, declaration)
            if (decl is RosGlobalDecl) globals += declaration
        }
        CachedValueProvider.Result.create(ScopeIndex(byScope, globals), file)
    }

    /** Declarations visible at [place], innermost first; each name appears once. */
    fun visibleAt(place: PsiElement): List<Declaration> {
        val result = linkedMapOf<String, Declaration>()
        walk(place) { declaration ->
            result.putIfAbsent(declaration.name, declaration)
            false
        }
        val file = place.containingFile ?: return result.values.toList()
        for (global in index(file).globals) result.putIfAbsent(global.name, global)
        return result.values.toList()
    }

    /** The innermost declaration of [name] visible at [place], or null. */
    fun find(place: PsiElement, name: String): Declaration? {
        var found: Declaration? = null
        walk(place) { declaration ->
            if (declaration.name == name) found = declaration
            found != null
        }
        found?.let { return it }
        val file = place.containingFile ?: return null
        return index(file).globals.firstOrNull { it.name == name }
    }

    /** Visits declarations from the innermost scope outwards; stops when [visitor] returns true. */
    private fun walk(place: PsiElement, visitor: (Declaration) -> Boolean) {
        val file = place.containingFile ?: return
        val index = index(file)
        var child: PsiElement = place
        var scope: PsiElement? = place.parent
        while (scope != null) {
            val entries = index.byScope[scope]
            if (entries != null) {
                val limit = child.textRange.startOffset
                for (i in entries.indices.reversed()) {
                    val entry = entries[i]
                    if (entry.offset < limit && visitor(entry.declaration)) return
                }
            }
            if (isLoopBody(scope, child)) {
                loopVariable(scope)?.let { loop ->
                    for (name in loopVariableNames(scope)) {
                        if (visitor(Declaration(name, loop.element, Kind.LOOP))) return
                    }
                }
            }
            if (scope is PsiFile) return
            child = scope
            scope = scope.parent
        }
    }

    /** Loop variables are bound in `do={…}` only, not in `in=$list` or `from=`. */
    private fun isLoopBody(statement: PsiElement, child: PsiElement): Boolean =
        statement is RosScriptCommand && child is RosNamedArgument && RosPsiUtil.argumentName(child) == "do"

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
