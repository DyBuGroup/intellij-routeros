package dev.dybug.routeros.editor

import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.impl.RosStringManipulator

/** Surround With (Ctrl+Alt+T) for the selected text: braces, parentheses, brackets, quotes, `:do … on-error`. */
class RosSurroundDescriptor : SurroundDescriptor {
    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement> {
        var start = file.findElementAt(startOffset)
        var end = file.findElementAt(endOffset - 1)
        while (start is PsiWhiteSpace) start = start.nextSibling
        while (end is PsiWhiteSpace) end = end.prevSibling
        if (start == null || end == null || start.textRange.startOffset > end.textRange.endOffset) return PsiElement.EMPTY_ARRAY
        return if (start == end) arrayOf(start) else arrayOf(start, end)
    }

    override fun getSurrounders(): Array<Surrounder> = SURROUNDERS

    override fun isExclusive(): Boolean = false

    private class TextSurrounder(private val key: String, private val wrap: (String) -> String) : Surrounder {
        override fun getTemplateDescription(): String = RosBundle.message(key)

        override fun isApplicable(elements: Array<PsiElement>): Boolean = elements.isNotEmpty()

        override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
            val start = elements.first().textRange.startOffset
            val end = elements.last().textRange.endOffset
            val document = editor.document
            val text = document.getText(TextRange(start, end))
            val wrapped = wrap(text)
            document.replaceString(start, end, wrapped)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            return TextRange.from(start + wrapped.length, 0)
        }
    }

    private companion object {
        val SURROUNDERS: Array<Surrounder> = arrayOf(
            TextSurrounder("surround.braces") { "{ $it }" },
            TextSurrounder("surround.parens") { "($it)" },
            TextSurrounder("surround.brackets") { "[$it]" },
            TextSurrounder("surround.quotes") { "\"" + RosStringManipulator.escape(it) + "\"" },
            TextSurrounder("surround.do") { ":do { $it } on-error={ }" },
        )
    }
}
