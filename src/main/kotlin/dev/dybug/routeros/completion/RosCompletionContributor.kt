package dev.dybug.routeros.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPath
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosScopes
import dev.dybug.routeros.psi.RosString
import dev.dybug.routeros.psi.RosTokenSets
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.psi.RosWhereClause
import dev.dybug.routeros.schema.RosSchema
import dev.dybug.routeros.schema.RosSchemaContext
import dev.dybug.routeros.schema.RosSchemaService
import dev.dybug.routeros.schema.SchemaArg
import dev.dybug.routeros.schema.SchemaNode
import dev.dybug.routeros.schema.ValueType

/**
 * Schema-driven completion: menu paths and command words, property names (`name=`), property
 * values, `:` script commands and their arguments, and variables in scope.
 */
class RosCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val schema = RosSchemaService.schema(parameters.position.project) ?: return
        when (position.elementType) {
            RosTypes.DOLLAR_NAME -> completeVariables(position, result.withPrefixMatcher(prefix(parameters, 1)))
            in RosTokenSets.SCRIPT_COMMANDS -> completeScriptCommands(schema, result.withPrefixMatcher(prefix(parameters, 1)))
            RosTypes.PATH_WORD -> completePath(position, schema, result.withPrefixMatcher(prefix(parameters, 0)))
            RosTypes.STRING_TEXT -> {
                // action="dr|": complete the value inside the quotes
                val string = position.parent as? RosString ?: return
                val argument = string.parent as? RosNamedArgument ?: return
                if (RosPsiUtil.argumentValue(argument) === string) completeValues(argument, schema, result.withPrefixMatcher(prefix(parameters, 0)))
            }
            RosTypes.WORD, RosTypes.ARG_NAME, RosTypes.CONTROL_ARG ->
                completeArgumentPosition(position, schema, result.withPrefixMatcher(prefix(parameters, 0)))
        }
    }

    /** The typed text of the current token up to the caret, without a leading `$` or `:`. */
    private fun prefix(parameters: CompletionParameters, skip: Int): String {
        val position = parameters.position
        val offsetInToken = parameters.offset - position.textRange.startOffset
        return position.text.take(offsetInToken.coerceAtLeast(0)).drop(skip)
    }

    // ------------------------------------------------------------------ menu paths and command words

    private fun completePath(position: PsiElement, schema: RosSchema, result: CompletionResultSet) {
        val path = position.parent as? RosPath ?: return
        val command = path.parent as? RosCommand ?: return
        val before = generateSequence(path.firstChild) { it.nextSibling }
            .takeWhile { it != position }
            .filter { it.elementType == RosTypes.PATH_WORD }
            .map { it.text }
            .toList()
        val base = if (RosPsiUtil.isAbsolute(path)) emptyList() else RosPsiUtil.menuContextAt(command)
        val segments = resolve(base, before)
        val node = schema.node(segments)
        if (node == null || node.kind == SchemaNode.Kind.COMMAND) {
            // after the command word the lexer still sees path words: `/ip address add inter|`
            val target = schema.resolveCommand(segments)
            if (target.isKnown && (target.verb != null || target.command != null)) {
                completeArgumentsOf(command, schema, result, schema.argumentsOf(target))
            }
            return
        }
        for (child in node.children.values) {
            result.addElement(
                LookupElementBuilder.create(child, child.name)
                    .withIcon(if (child.isMenu) AllIcons.Nodes.Folder else AllIcons.Nodes.Method)
                    .withTypeText(if (child.isMenu) "menu" else "command")
                    .withTailText(child.packageName?.let { " ($it)" }, true),
            )
        }
        for (verb in schema.commandsOf(node) - node.children.keys) {
            result.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create(verb).withIcon(AllIcons.Nodes.Method).withTypeText("command").bold(),
                    1.0,
                ),
            )
        }
        if (before.isEmpty() && !RosPsiUtil.isAbsolute(path)) {
            completeArgumentsOf(command, schema, result)
        }
    }

    private fun resolve(base: List<String>, segments: List<String>): List<String> {
        val result = base.toMutableList()
        for (segment in segments) {
            if (segment == "..") result.removeLastOrNull() else result += segment
        }
        return result
    }

    // ------------------------------------------------------------------ arguments and values

    private fun completeArgumentPosition(position: PsiElement, schema: RosSchema, result: CompletionResultSet) {
        val parent = position.parent
        if (parent is RosNamedArgument && RosPsiUtil.argumentValue(parent) === position) {
            completeValues(parent, schema, result)
            return
        }
        val where = PsiTreeUtil.getParentOfType(position, RosWhereClause::class.java)
        val owner = RosSchemaContext.owningStatement(position) ?: return
        if (where != null && owner is RosCommand) {
            val target = RosSchemaContext.target(owner, schema)
            val menu = target.menu ?: return
            val args = menu.args + menu.readOnly + menu.flags.values.associateBy { it.rawType }
            for ((name, arg) in args) {
                result.addElement(LookupElementBuilder.create(arg, name).withIcon(AllIcons.Nodes.Property).withTypeText(describe(arg)))
            }
            return
        }
        // a positional enum such as the topic of :log
        RosSchemaContext.scriptCommand(owner, schema)?.positionalValues?.forEach {
            result.addElement(LookupElementBuilder.create(it).withIcon(AllIcons.Nodes.Enum))
        }
        completeArgumentsOf(owner, schema, result)
    }

    private fun completeArgumentsOf(
        owner: PsiElement,
        schema: RosSchema,
        result: CompletionResultSet,
        arguments: Map<String, SchemaArg> = RosSchemaContext.argumentsFor(owner, schema),
    ) {
        val used = RosPsiUtil.namedArguments(owner).map(RosPsiUtil::argumentName).toSet()
        for ((name, arg) in arguments) {
            if (name in used) continue
            val element = LookupElementBuilder.create(arg, name)
                .withIcon(if (arg.mandatory) AllIcons.Nodes.PropertyWriteStatic else AllIcons.Nodes.Property)
                .withTypeText(describe(arg))
                .withInsertHandler(if (arg.type is ValueType.Switch) null else EqualsInsertHandler)
                .withStrikeoutness(arg.deprecated)
            result.addElement(if (arg.mandatory) PrioritizedLookupElement.withPriority(element.bold(), 2.0) else element)
        }
    }

    private fun completeValues(argument: RosNamedArgument, schema: RosSchema, result: CompletionResultSet) {
        val owner = RosSchemaContext.owningStatement(argument) ?: return
        val name = RosPsiUtil.argumentName(argument)
        val arg = RosSchemaContext.argumentsFor(owner, schema)[name]
        val type = arg?.type
        val suggestions = linkedSetOf<String>()
        type?.let { suggestions += ValueType.suggestions(it) }
        if (type is ValueType.IfaceEnum) suggestions += RosProjectValues.interfaceNames(argument.containingFile as RosFile)
        if (type == null || type is ValueType.Enum && type.dynamic || type is ValueType.Str || type is ValueType.Unknown) {
            suggestions += RosProjectValues.valuesUsedFor(argument.containingFile as RosFile, name)
        }
        for (value in suggestions) {
            if (value.isBlank() || value.contains(' ') || value.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) continue
            result.addElement(LookupElementBuilder.create(value).withIcon(AllIcons.Nodes.Enum))
        }
    }

    // ------------------------------------------------------------------ script commands and variables

    private fun completeScriptCommands(schema: RosSchema, result: CompletionResultSet) {
        for (command in schema.builtins.scriptCommands.values) {
            result.addElement(
                LookupElementBuilder.create(command, command.name)
                    .withPresentableText(":" + command.name)
                    .withIcon(AllIcons.Nodes.Function)
                    .withTailText(" " + command.syntax.substringAfter(' ', "").take(50), true)
                    .withLookupString(":" + command.name),
            )
        }
    }

    private fun completeVariables(position: PsiElement, result: CompletionResultSet) {
        for (declaration in RosScopes.visibleAt(position)) {
            val icon = when (declaration.kind) {
                RosScopes.Kind.FUNCTION_GLOBAL, RosScopes.Kind.FUNCTION_LOCAL -> AllIcons.Nodes.Function
                else -> AllIcons.Nodes.Variable
            }
            result.addElement(
                LookupElementBuilder.create(declaration.name)
                    .withPresentableText("$" + declaration.name)
                    .withIcon(icon)
                    .withTypeText(declaration.kind.name.lowercase().replace('_', ' ')),
            )
        }
        if (PsiTreeUtil.getParentOfType(position, RosBlock::class.java) != null) {
            for (i in 1..9) result.addElement(LookupElementBuilder.create(i.toString()).withPresentableText("$$i").withTypeText("argument"))
        }
    }

    private fun describe(arg: SchemaArg): String = ValueType.describe(arg.type).take(40)

    /** Adds `=` after a property name unless one is already there, and opens value completion. */
    private object EqualsInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val document = context.document
            val tail = context.tailOffset
            if (tail >= document.textLength || document.charsSequence[tail] != '=') {
                document.insertString(tail, "=")
            }
            context.editor.caretModel.moveToOffset(tail + 1)
            AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
        }
    }
}
