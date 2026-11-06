package dev.dybug.routeros.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPath
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosTokenSets
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.psi.RosWhereClause
import dev.dybug.routeros.schema.RosSchema
import dev.dybug.routeros.schema.RosSchemaContext
import dev.dybug.routeros.schema.RosSchemaService
import dev.dybug.routeros.schema.SchemaArg
import dev.dybug.routeros.schema.SchemaNode
import dev.dybug.routeros.schema.ScriptCommand
import dev.dybug.routeros.schema.ValueType

/** Quick documentation (Ctrl+Q) for menus, commands, properties and `:` commands, from the CLI reference. */
class RosDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? {
        if (file !is RosFile || contextElement == null) return null
        val schema = RosSchemaService.schema(file.project) ?: return null
        val html = docFor(contextElement, schema) ?: return null
        return RosDocElement(contextElement, contextElement.text, html)
    }

    override fun getDocumentationElementForLookupItem(psiManager: PsiManager, item: Any?, element: PsiElement?): PsiElement? {
        val context = element ?: return null
        val html = when (item) {
            is SchemaNode -> nodeDoc(item)
            is SchemaArg -> argDoc(item, null)
            is ScriptCommand -> scriptCommandDoc(item)
            else -> null
        } ?: return null
        return RosDocElement(context, item.toString(), html)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? = (element as? RosDocElement)?.html

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        (element as? RosDocElement)?.html?.let { StringUtil.removeHtmlTags(it).take(200) }

    private fun docFor(leaf: PsiElement, schema: RosSchema): String? {
        val type = leaf.elementType
        return when {
            type in RosTokenSets.SCRIPT_COMMANDS -> schema.builtins.scriptCommands[leaf.text.removePrefix(":")]?.let(::scriptCommandDoc)
            type == RosTypes.PATH_WORD -> pathDoc(leaf, schema)
            type == RosTypes.ARG_NAME || type == RosTypes.CONTROL_ARG -> argumentDoc(leaf, schema)
            type == RosTypes.WORD && PsiTreeUtil.getParentOfType(leaf, RosWhereClause::class.java) != null -> whereDoc(leaf, schema)
            else -> null
        }
    }

    private fun pathDoc(leaf: PsiElement, schema: RosSchema): String? {
        val path = leaf.parent as? RosPath ?: return null
        val command = path.parent as? RosCommand ?: return null
        val words = generateSequence(path.firstChild) { it.nextSibling }.filter { it.elementType == RosTypes.PATH_WORD }.toList()
        val upTo = words.takeWhile { it !== leaf }.map { it.text } + leaf.text
        val base = if (RosPsiUtil.isAbsolute(path)) emptyList() else RosPsiUtil.menuContextAt(command)
        val segments = resolve(base, upTo)
        schema.node(segments)?.let { return nodeDoc(it) }
        val menu = schema.node(segments.dropLast(1)) ?: return null
        val verb = segments.last()
        if (verb !in schema.commandsOf(menu)) return null
        val (syntax, doc) = schema.builtins.commandDocs[verb] ?: return section("/${menu.path} $verb", null, null)
        return section("/${menu.path} $verb", doc, syntax)
    }

    private fun argumentDoc(leaf: PsiElement, schema: RosSchema): String? {
        val argument = leaf.parent as? RosNamedArgument ?: return null
        val owner = RosSchemaContext.owningStatement(argument) ?: return null
        val arg = RosSchemaContext.argumentsFor(owner, schema)[RosPsiUtil.argumentName(argument)] ?: return null
        val menu = (owner as? RosCommand)?.let { RosSchemaContext.target(it, schema) }
        return argDoc(arg, menu?.command ?: menu?.menu)
    }

    private fun whereDoc(leaf: PsiElement, schema: RosSchema): String? {
        val command = PsiTreeUtil.getParentOfType(leaf, RosCommand::class.java) ?: return null
        val menu = RosSchemaContext.target(command, schema).menu ?: return null
        val arg = menu.args[leaf.text] ?: menu.readOnly[leaf.text] ?: return null
        return argDoc(arg, menu)
    }

    private fun resolve(base: List<String>, segments: List<String>): List<String> {
        val result = base.toMutableList()
        for (segment in segments) if (segment == "..") result.removeLastOrNull() else result += segment
        return result
    }

    companion object {
        fun nodeDoc(node: SchemaNode): String {
            val kind = when (node.kind) {
                SchemaNode.Kind.DIRECTORY -> "menu"
                SchemaNode.Kind.SETTINGS -> "settings menu"
                SchemaNode.Kind.COMMAND -> "command"
                SchemaNode.Kind.IMPLICIT -> "menu"
            }
            val extra = buildString {
                node.packageName?.let { append("<p>Package: <code>").append(escape(it)).append("</code></p>") }
                append("<p><a href=\"").append(escape(node.docUrl)).append("\">CLI reference</a></p>")
            }
            return section("/${node.path.replace('/', ' ')}", node.doc, kind, extra)
        }

        fun argDoc(arg: SchemaArg, owner: SchemaNode?): String {
            val type = ValueType.describe(arg.type)
            val flags = listOfNotNull("mandatory".takeIf { arg.mandatory }, "deprecated".takeIf { arg.deprecated }, "can be unset with !".takeIf { arg.unsettable })
            val extra = buildString {
                if (arg.rawType.isNotEmpty()) append("<p>Type: <code>").append(escape(arg.rawType.take(300))).append("</code></p>")
                if (flags.isNotEmpty()) append("<p>").append(flags.joinToString(", ")).append("</p>")
                owner?.let { append("<p><a href=\"").append(escape(it.docUrl)).append("\">CLI reference</a></p>") }
            }
            return section(arg.name, arg.doc, type, extra)
        }

        fun scriptCommandDoc(command: ScriptCommand): String = section(":" + command.name, command.doc, command.syntax)

        private fun section(title: String, doc: String?, subtitle: String?, extra: String = ""): String = buildString {
            append(DocumentationMarkup.DEFINITION_START).append(escape(title))
            if (!subtitle.isNullOrBlank()) append("<br/><span style=\"color:gray\">").append(escape(subtitle)).append("</span>")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            if (doc != null) append(markdown(doc))
            append(extra)
            append(DocumentationMarkup.CONTENT_END)
        }

        private fun escape(text: String) = StringUtil.escapeXmlEntities(text)

        /** Minimal Markdown: code spans, bullet lines and paragraphs; links keep their text only. */
        fun markdown(text: String): String {
            val escaped = escape(text)
                .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
                .replace(Regex("`([^`]+)`"), "<code>$1</code>")
            return escaped.split(Regex("\n{2,}")).joinToString("") { paragraph ->
                val lines = paragraph.lines()
                if (lines.any { it.trimStart().startsWith("- ") }) {
                    "<ul>" + lines.joinToString("") { line ->
                        if (line.trimStart().startsWith("- ")) "<li>" + line.trimStart().removePrefix("- ") + "</li>" else "$line "
                    } + "</ul>"
                } else {
                    "<p>" + lines.joinToString(" ") + "</p>"
                }
            }
        }
    }
}

/** Carries generated documentation; never part of the PSI tree. */
class RosDocElement(private val context: PsiElement, private val title: String, val html: String) : FakePsiElement() {
    override fun getParent(): PsiElement = context

    override fun getName(): String = title

    override fun getPresentableText(): String = title
}
