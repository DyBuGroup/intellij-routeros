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
 * - `:global` is visible after its declaration anywhere in the file (it ignores blocks), and
 *   project-wide through the global index.
 *
 * Declarations are collected once per file modification into [ScopeIndex]; a name lookup is a
 * binary search per enclosing scope.
 */
object RosScopes {
    enum class Kind { LOCAL, GLOBAL, LOOP, FUNCTION_LOCAL, FUNCTION_GLOBAL }

    data class Declaration(val name: String, val element: PsiElement, val kind: Kind)

    private class Entry(val offset: Int, val declaration: Declaration)

    private class ScopeIndex(
        /** scope (block, bracket substitution or file) → its direct declarations, in offset order */
        val byScope: Map<PsiElement, List<Entry>>,
        /** scope → name → that scope's declarations of the name, in offset order */
        val byScopeAndName: Map<PsiElement, Map<String, List<Entry>>>,
        /** `:global` declarations of the file in offset order; `:global` ignores blocks */
        val globals: List<Entry>,
        val globalsByName: Map<String, List<Entry>>,
    )

    private fun index(file: PsiFile): ScopeIndex = CachedValuesManager.getCachedValue(file) {
        val byScope = HashMap<PsiElement, MutableList<Entry>>()
        val globals = mutableListOf<Entry>()
        for (decl in PsiTreeUtil.findChildrenOfAnyType(file, RosLocalDecl::class.java, RosGlobalDecl::class.java)) {
            val declaration = declaration(decl) ?: continue
            val entry = Entry(decl.textRange.startOffset, declaration)
            byScope.getOrPut(decl.parent) { mutableListOf() } += entry
            if (decl is RosGlobalDecl) globals += entry
        }
        val byScopeAndName = byScope.mapValues { (_, entries) -> entries.groupBy { it.declaration.name } }
        CachedValueProvider.Result.create(ScopeIndex(byScope, byScopeAndName, globals, globals.groupBy { it.declaration.name }), file)
    }

    /** Declarations visible at [place], innermost first; each name appears once. */
    fun visibleAt(place: PsiElement): List<Declaration> {
        val result = linkedMapOf<String, Declaration>()
        walk(place) { declaration ->
            result.putIfAbsent(declaration.name, declaration)
            false
        }
        val file = place.containingFile ?: return result.values.toList()
        val offset = place.textRange.startOffset
        for (global in index(file).globals) {
            if (global.offset < offset) result.putIfAbsent(global.declaration.name, global.declaration)
        }
        return result.values.toList()
    }

    /** The innermost declaration of [name] visible at [place], or null. Cost: O(depth × log n). */
    fun find(place: PsiElement, name: String): Declaration? {
        val file = place.containingFile ?: return null
        val index = index(file)
        var child: PsiElement = place
        var scope: PsiElement? = place.parent
        while (scope != null) {
            index.byScopeAndName[scope]?.get(name)?.let { entries ->
                lastBefore(entries, child.textRange.startOffset)?.let { return it.declaration }
            }
            if (isLoopBody(scope, child) && name in loopVariableNames(scope)) {
                loopVariable(scope)?.let { return Declaration(name, it.element, Kind.LOOP) }
            }
            if (scope is PsiFile) break
            child = scope
            scope = scope.parent
        }
        return index.globalsByName[name]?.let { lastBefore(it, place.textRange.startOffset) }?.declaration
    }

    /** `:global` declarations of [name] later in the file than [place] (used before declared). */
    fun declaredLater(place: PsiElement, name: String): Boolean {
        val file = place.containingFile ?: return false
        return index(file).globalsByName[name]?.any { it.offset > place.textRange.startOffset } == true
    }

    private fun lastBefore(entries: List<Entry>, offset: Int): Entry? {
        var low = 0
        var high = entries.size - 1
        var found: Entry? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (entries[mid].offset < offset) {
                found = entries[mid]
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    /** Visits declarations from the innermost scope outwards (completion); stops when [visitor] returns true. */
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
        val name = statement.firstChild?.text?.removePrefix(":") ?: return null
        if (name != "foreach" && name != "for" && name != "onerror") return null
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
