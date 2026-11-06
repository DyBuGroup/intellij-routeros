package dev.dybug.routeros.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.inspections.RosQuickFixes
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosBracketCommand
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPath
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosString
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.psi.impl.RosStringManipulator

private fun replaceRange(project: Project, element: PsiElement, start: Int, end: Int, text: String) {
    val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return
    dev.dybug.routeros.editor.RosDocumentEdits.replace(project, document, start, end, text)
}

/** `/ip firewall filter` ⇄ `/ip/firewall/filter`. */
class RosConvertPathStyleIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = RosBundle.message("intention.convert.path")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val path = PsiTreeUtil.getParentOfType(element, RosPath::class.java, false) ?: return false
        if (menuAndCommandWords(path, RosPsiUtil.pathSegments(path)) < 2) return false
        text = RosBundle.message(if (usesSpaces(path)) "intention.convert.path.slash" else "intention.convert.path.space")
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val path = PsiTreeUtil.getParentOfType(element, RosPath::class.java, false) ?: return
        val segments = RosPsiUtil.pathSegments(path)
        // words after the command word are positional arguments (`run foo`, `print detail`) and stay separate
        val count = menuAndCommandWords(path, segments)
        if (count < 2) return
        val prefix = if (RosPsiUtil.isAbsolute(path)) "/" else ""
        val separator = if (usesSpaces(path)) "/" else " "
        val converted = prefix + segments.take(count).joinToString(separator) +
            segments.drop(count).joinToString("") { " $it" }
        replaceRange(project, path, path.textRange.startOffset, path.textRange.endOffset, converted)
    }

    /** Number of leading words that are menus or the command word, according to the schema. */
    private fun menuAndCommandWords(path: RosPath, segments: List<String>): Int {
        val schema = dev.dybug.routeros.schema.RosSchemaService.schema(path.project) ?: return segments.size
        val command = path.parent as? dev.dybug.routeros.psi.RosCommand ?: return segments.size
        val base = if (RosPsiUtil.isAbsolute(path)) emptyList() else RosPsiUtil.menuContextAt(command)
        if (segments.any { it == ".." }) return segments.size
        val target = schema.resolveCommand(base + segments)
        return (target.consumed - base.size).coerceIn(0, segments.size)
    }

    /** True if any two consecutive path words are separated by whitespace rather than '/'. */
    private fun usesSpaces(path: RosPath): Boolean {
        val words = path.node.getChildren(null).filter { it.elementType == RosTypes.PATH_WORD }
        return words.zipWithNext().any { (a, _) -> a.treeNext?.elementType != RosTypes.SLASH }
    }
}

/** `name=value` ⇄ `name="value"`. */
class RosToggleQuotesIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = RosBundle.message("intention.toggle.quotes")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val argument = PsiTreeUtil.getParentOfType(element, RosNamedArgument::class.java) ?: return false
        // in function and script-command calls quotes decide the type ("10" is a string, 10 a number)
        if (argument.parent !is dev.dybug.routeros.psi.RosCommand) return false
        val value = RosPsiUtil.argumentValue(argument) ?: return false
        return when {
            value.elementType == RosTypes.WORD -> true.also { text = RosBundle.message("intention.add.quotes") }
            value is RosString && canUnquote(value) -> true.also { text = RosBundle.message("intention.remove.quotes") }
            else -> false
        }
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val argument = PsiTreeUtil.getParentOfType(element, RosNamedArgument::class.java) ?: return
        val value = RosPsiUtil.argumentValue(argument) ?: return
        val replacement = if (value is RosString) RosPsiUtil.stringValue(value) else "\"" + RosStringManipulator.escape(value.text) + "\""
        replaceRange(project, value, value.textRange.startOffset, value.textRange.endOffset, replacement)
    }

    private fun canUnquote(string: RosString): Boolean {
        val content = RosPsiUtil.stringValue(string)
        return content.isNotEmpty() && content.all { it.isLetterOrDigit() || it in "-_.,:/@+*" } &&
            string.node.getChildren(null).all { it.elementType == RosTypes.STRING_START || it.elementType == RosTypes.STRING_END || it.elementType == RosTypes.STRING_TEXT }
    }
}

/** Wraps a statement in `:do { … } on-error={ … }`. */
class RosWrapOnErrorIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = RosBundle.message("intention.wrap.onerror")

    override fun getText(): String = familyName

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val statement = statement(element) ?: return false
        return statement.elementType != RosTypes.NEWLINE && statement.textLength > 0
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val statement = statement(element) ?: return
        val range = statement.textRange
        replaceRange(project, statement, range.startOffset, range.endOffset, ":do { ${statement.text} } on-error={ :log warning \"failed\" }")
    }

    private fun statement(element: PsiElement): PsiElement? {
        val candidate = RosQuickFixes.statementOf(element)
        val parent = candidate.parent
        if (parent !is RosFile && parent !is RosBlock && parent !is RosBracketCommand) return null
        if (candidate.firstChild == null) return null
        // wrapping a declaration in a block would hide the variable from the following statements
        if (candidate is RosDeclaration) return null
        // a bare menu path selects the menu for the following lines; inside a block it would not
        if (candidate is dev.dybug.routeros.psi.RosCommand && RosPsiUtil.isMenuContextLine(candidate)) return null
        return candidate
    }
}
