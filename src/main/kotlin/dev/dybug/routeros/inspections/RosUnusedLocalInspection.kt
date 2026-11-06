package dev.dybug.routeros.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosLocalDecl
import dev.dybug.routeros.psi.RosSetStatement
import dev.dybug.routeros.psi.RosVariable
import dev.dybug.routeros.references.RosSetTargetReference
import dev.dybug.routeros.references.RosVariableReference

class RosUnusedLocalInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosLocalDecl) return
        val identifier = element.nameIdentifier ?: return
        val name = element.name ?: return
        if (element in usedDeclarations(element.containingFile)) return
        holder.registerProblem(identifier, RosBundle.message("inspection.unused.local", name), ProblemHighlightType.LIKE_UNUSED_SYMBOL)
    }

    /** Locals referenced anywhere in the file, computed in one pass (a search per local would be quadratic). */
    private fun usedDeclarations(file: PsiFile): Set<PsiElement> = CachedValuesManager.getCachedValue(file) {
        val used = HashSet<PsiElement>()
        for (element in PsiTreeUtil.findChildrenOfAnyType(file, RosVariable::class.java, RosSetStatement::class.java)) {
            val target = when (val reference = element.reference) {
                is RosVariableReference -> reference.resolveLocally()
                is RosSetTargetReference -> reference.resolveLocally()
                else -> null
            }
            if (target != null) used += target
        }
        CachedValueProvider.Result.create(used, file)
    }
}
