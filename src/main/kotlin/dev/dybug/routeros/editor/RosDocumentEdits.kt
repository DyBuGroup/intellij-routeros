package dev.dybug.routeros.editor

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import dev.dybug.routeros.psi.impl.RosStringManipulator

/**
 * Text edits for quick fixes, intentions and surrounders. Inside RouterOS injected into a string
 * (`source="…"`) the text lands in the host string literal, so it must be escaped there.
 */
object RosDocumentEdits {
    fun replace(project: Project, document: Document, start: Int, end: Int, text: String) {
        document.replaceString(start, end, forDocument(document, text))
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    fun insert(project: Project, document: Document, offset: Int, text: String) = replace(project, document, offset, offset, text)

    fun forDocument(document: Document, text: String): String =
        if (document is DocumentWindow) RosStringManipulator.escape(text) else text
}
