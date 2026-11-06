package dev.dybug.routeros.references

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosElementFactory
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosSetStatement
import dev.dybug.routeros.psi.RosVariable

/**
 * Word-based search cannot find quoted or escaped spellings such as `$"my-var"` or `$"a\62"`.
 * This searcher scans the candidate files — the declaring file for locals; for a `:global`, the
 * files that declare it (a script must declare a global to use it) — for such references.
 */
class RosReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val target = parameters.elementToSearch as? RosDeclaration ?: return
        val name = target.name ?: return
        val files = when {
            !target.isGlobal -> listOfNotNull(target.containingFile as? RosFile)
            else -> RosGlobalIndex.declarations(target.project, name).mapNotNull { it.containingFile as? RosFile }.distinct()
        }
        val scope = parameters.effectiveSearchScope
        for (file in files) {
            for (reference in quotedReferences(file)) {
                if (!PsiSearchScopeUtil.isInScope(scope, reference.element)) continue
                if (reference.isReferenceTo(target) && !consumer.process(reference)) return
            }
        }
    }

    /** References whose name is not a plain word in the source; usually few, so each query is cheap. */
    private fun quotedReferences(file: RosFile): List<PsiReference> = CachedValuesManager.getCachedValue(file) {
        val result = PsiTreeUtil.findChildrenOfAnyType(file, RosVariable::class.java, RosSetStatement::class.java)
            .mapNotNull { element ->
                element.reference?.takeIf { !RosElementFactory.isPlainName(it.rangeInElement.substring(element.text)) }
            }
        CachedValueProvider.Result.create(result, file)
    }
}
