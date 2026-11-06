package dev.dybug.routeros.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosLocalDecl

class RosUnusedLocalInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosLocalDecl) return
        val identifier = element.nameIdentifier ?: return
        val name = element.name ?: return
        if (ReferencesSearch.search(element, LocalSearchScope(element.containingFile)).findFirst() != null) return
        holder.registerProblem(
            identifier,
            RosBundle.message("inspection.unused.local", name),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        )
    }
}
