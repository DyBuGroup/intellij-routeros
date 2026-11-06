package dev.dybug.routeros.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosStrayToken
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.schema.RosSchema
import dev.dybug.routeros.schema.RosSchemaContext
import dev.dybug.routeros.schema.RosSchemaService

/**
 * Semantic highlighting from the schema (command words vs menus vs positional values) and
 * syntax errors that the parser accepts (unmatched brackets, invalid escapes). Schema-based
 * warnings live in inspections so that users can tune them.
 */
class RosAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is RosStrayToken -> holder.newAnnotation(HighlightSeverity.ERROR, RosBundle.message("annotator.unmatched", element.text)).create()
            is RosCommand -> annotateCommand(element, holder)
            else -> if (element.elementType == RosTypes.STRING_INVALID_ESCAPE) {
                holder.newAnnotation(HighlightSeverity.ERROR, RosBundle.message("annotator.invalid.escape", element.text)).create()
            }
        }
    }

    private fun annotateCommand(command: RosCommand, holder: AnnotationHolder) {
        val schema = RosSchemaService.schema(command.project) ?: return
        val path = command.path
        val target = RosSchemaContext.target(command, schema)
        if (path != null) colorPath(command, target, holder)
    }

    private fun colorPath(command: RosCommand, target: RosSchema.CommandTarget, holder: AnnotationHolder) {
        val path = command.path ?: return
        val words = generateSequence(path.firstChild) { it.nextSibling }.filter { it.elementType == RosTypes.PATH_WORD }.toList()
        if (words.isEmpty()) return
        val contextSize = if (RosPsiUtil.isAbsolute(path)) 0 else RosPsiUtil.menuContextAt(command).size
        // index (within this path) of the command word, if it was recognised
        val verbIndex = if (target.verb != null) target.consumed - 1 - contextSize else -1
        val dotDot = words.count { it.text == ".." }
        words.forEachIndexed { index, word ->
            val key = when {
                dotDot > 0 -> return@forEachIndexed
                index == verbIndex -> RosHighlighterColors.COMMAND
                verbIndex in 0 until index -> RosHighlighterColors.VALUE
                else -> return@forEachIndexed
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(word).textAttributes(key).create()
        }
    }
}
