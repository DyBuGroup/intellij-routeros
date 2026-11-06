package dev.dybug.routeros.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosStrayToken
import dev.dybug.routeros.psi.RosString
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.schema.RosSchema
import dev.dybug.routeros.schema.RosSchemaContext
import dev.dybug.routeros.schema.RosSchemaService
import dev.dybug.routeros.schema.ValueTypeDetector

/**
 * Semantic highlighting from the schema (command words vs menus vs positional values) and
 * conservative weak warnings for unknown properties and values that clearly do not fit.
 */
class RosAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is RosStrayToken -> holder.newAnnotation(HighlightSeverity.ERROR, RosBundle.message("annotator.unmatched", element.text)).create()
            is RosCommand -> annotateCommand(element, holder)
        }
    }

    private fun annotateCommand(command: RosCommand, holder: AnnotationHolder) {
        val schema = RosSchemaService.schema() ?: return
        val path = command.path
        val target = RosSchemaContext.target(command, schema)
        if (path != null) colorPath(command, target, holder)
        if (!target.isKnown) return
        val arguments = schema.argumentsOf(target)
        val checkNames = arguments.isNotEmpty() && (target.command != null || target.verb == "add" || target.verb == "set")
        for (argument in RosPsiUtil.namedArguments(command)) {
            annotateArgument(argument, arguments, checkNames, schema, holder)
        }
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

    private fun annotateArgument(
        argument: RosNamedArgument,
        arguments: Map<String, dev.dybug.routeros.schema.SchemaArg>,
        checkNames: Boolean,
        schema: RosSchema,
        holder: AnnotationHolder,
    ) {
        val key = argument.firstChild ?: return
        if (key.elementType != RosTypes.ARG_NAME) return
        val name = key.text
        val arg = arguments[name]
        if (arg == null) {
            if (checkNames && !name.contains('.') && !name.startsWith("!") && name !in schema.builtins.universalArguments) {
                holder.newAnnotation(HighlightSeverity.WEAK_WARNING, RosBundle.message("annotator.unknown.property", name))
                    .range(key)
                    .create()
            }
            return
        }
        if (arg.deprecated) {
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, RosBundle.message("annotator.deprecated.property", name))
                .range(key)
                .create()
        }
        val value = RosPsiUtil.argumentValue(argument) ?: return
        if (value.elementType != RosTypes.WORD && value !is RosString) return
        val text = RosPsiUtil.argumentValueText(argument) ?: return
        val problem = ValueTypeDetector.problem(text, arg.type) ?: return
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING, RosBundle.message("annotator.bad.value", name, problem))
            .range(value)
            .create()
    }
}
