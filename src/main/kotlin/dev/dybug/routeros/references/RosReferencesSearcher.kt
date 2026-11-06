package dev.dybug.routeros.references

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosSetStatement
import dev.dybug.routeros.psi.RosVariable

/**
 * Word-based search cannot find escaped spellings such as `$"a\62"` for `ab`. This searcher scans
 * the candidate files (the declaring file for locals; files that declare the `:global` otherwise —
 * a script must declare a global to use it) for escaped references that resolve to the target.
 */
class RosReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    /** References whose name is written with escapes; usually none, so each query is cheap. */
    private fun escapedReferences(file: RosFile): List<PsiReference> = CachedValuesManager.getCachedValue(file) {
        val result = PsiTreeUtil.findChildrenOfAnyType(file, RosVariable::class.java, RosSetStatement::class.java)
            .mapNotNull { element -> element.reference?.takeIf { it.rangeInElement.substring(element.text).contains('\\') } }
        CachedValueProvider.Result.create(result, file)
    }

    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val target = parameters.elementToSearch as? RosDeclaration ?: return
        val name = target.name ?: return
        val files = when {
            !target.isGlobal -> listOfNotNull(target.containingFile as? RosFile)
            else -> RosGlobalIndex.declarations(target.project, name).mapNotNull { it.containingFile as? RosFile }.distinct()
        }
        val scope = parameters.effectiveSearchScope
        for (file in files) {
            val virtualFile = file.virtualFile
            if (scope is LocalSearchScope && !scope.isInScope(virtualFile)) continue
            for (reference in escapedReferences(file)) {
                if (reference.isReferenceTo(target) && !consumer.process(reference)) return
            }
        }
    }
}
