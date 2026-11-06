package dev.dybug.routeros.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosBracketCommand
import dev.dybug.routeros.psi.RosElementFactory
import dev.dybug.routeros.psi.RosFile

/** Text-based fixes: they edit the document directly, which keeps them robust for this permissive grammar. */
object RosQuickFixes {
    /** Inserts `:local name` / `:global name` on its own line before the statement containing the element. */
    class Declare(private val name: String, private val global: Boolean) : LocalQuickFix {
        override fun getFamilyName(): String = RosBundle.message(if (global) "fix.declare.global" else "fix.declare.local", name)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return
            val statement = statementOf(element)
            insertLineBefore(project, statement, (if (global) ":global " else ":local ") + RosElementFactory.nameText(name))
        }
    }

    class ReplaceText(private val familyKey: String, private val replacement: String, private vararg val args: Any) : LocalQuickFix {
        override fun getFamilyName(): String = RosBundle.message(familyKey, *args)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return
            val range = descriptor.textRangeInElement?.shiftRight(element.textRange.startOffset) ?: element.textRange
            replace(project, element, range.startOffset, range.endOffset, replacement)
        }
    }

    class Delete(private val familyKey: String) : LocalQuickFix {
        override fun getFamilyName(): String = RosBundle.message(familyKey)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val start = descriptor.startElement ?: return
            val end = descriptor.endElement ?: start
            replace(project, start, start.textRange.startOffset, end.textRange.endOffset, "")
        }
    }

    fun statementOf(element: PsiElement): PsiElement {
        var current = element
        while (current.parent != null && current.parent !is RosFile && current.parent !is RosBlock && current.parent !is RosBracketCommand) {
            current = current.parent
        }
        return current
    }

    fun insertLineBefore(project: Project, statement: PsiElement, text: String) {
        val document = PsiDocumentManager.getInstance(project).getDocument(statement.containingFile) ?: return
        val offset = statement.textRange.startOffset
        val line = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(line)
        val indent = document.charsSequence.subSequence(lineStart, offset).takeWhile { it == ' ' || it == '\t' }
        val onlyIndentBefore = document.charsSequence.subSequence(lineStart, offset).isBlank()
        if (onlyIndentBefore) {
            document.insertString(lineStart, "$indent$text\n")
        } else {
            document.insertString(offset, "$text; ")
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    fun replace(project: Project, element: PsiElement, start: Int, end: Int, text: String) {
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return
        document.replaceString(start, end, text)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
