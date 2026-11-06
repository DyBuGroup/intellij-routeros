package dev.dybug.routeros.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import dev.dybug.routeros.psi.RosFile

/** Visits every PSI element of a RouterOS file; subclasses override [visit]. */
abstract class RosInspectionBase : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (holder.file !is RosFile) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                visit(element, holder)
            }

            override fun visitFile(file: PsiFile) {
                visitRosFile(file as RosFile, holder)
            }
        }
    }

    protected open fun visit(element: PsiElement, holder: ProblemsHolder) {}

    protected open fun visitRosFile(file: RosFile, holder: ProblemsHolder) {}
}
