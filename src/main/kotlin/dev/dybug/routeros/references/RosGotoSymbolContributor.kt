package dev.dybug.routeros.references

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter

/** Go to Symbol: `:global` variables and functions across the project. */
class RosGotoSymbolContributor : ChooseByNameContributorEx {
    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        for (name in RosGlobalIndex.allNames(project)) {
            if (!processor.process(name)) return
        }
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        for (declaration in RosGlobalIndex.declarations(parameters.project, name, parameters.searchScope)) {
            if (declaration is NavigationItem && !processor.process(declaration)) return
        }
    }
}
