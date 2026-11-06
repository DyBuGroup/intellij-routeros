package dev.dybug.routeros.device.debug

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosScriptCommand
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosElementFactory
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosScopes
import dev.dybug.routeros.psi.RosTypes

/**
 * Rewrites a script for the experimental debugger. Before every statement that starts a line
 * (at top level or directly in a block) it inserts, on the same line, a pause point:
 *
 * ```
 * :if ($<p>Step or [:find $<p>Bps ",12,"] >= 0) do={ :set <p>State …vars as JSON…; :set <p>Pause true; :while ($<p>Pause) do={ :delay 100ms } };
 * ```
 *
 * Line numbers stay the same, so router errors still point at the user's lines. Variables are
 * the locals/globals visible at that statement (functions excluded), serialised with `:serialize`.
 */
class RosInstrumenter(val token: String) {
    init {
        require(token.all { it.isLetterOrDigit() }) { "token must be alphanumeric" }
    }

    val pauseVar = "RosDbg${token}Pause"
    val stateVar = "RosDbg${token}State"
    val stepVar = "RosDbg${token}Step"
    val breakpointsVar = "RosDbg${token}Bps"
    val sequenceVar = "RosDbg${token}Seq"

    data class Result(val text: String, val pauseLines: Set<Int>)

    /** [file] must be the parsed original text; lines are 0-based. */
    fun instrument(file: RosFile): Result {
        val text = file.text
        val insertions = sortedMapOf<Int, String>()
        val lines = mutableSetOf<Int>()
        val document = LineIndex(text)
        for (statement in statements(file)) {
            val offset = statement.textRange.startOffset
            val line = document.lineOf(offset)
            if (line in lines || !startsLine(text, offset)) continue
            lines += line
            insertions[offset] = pausePoint(line, variables(statement))
        }
        val result = StringBuilder(text.length + insertions.size * 160)
        var last = 0
        for ((offset, code) in insertions) {
            result.append(text, last, offset).append(code)
            last = offset
        }
        result.append(text, last, text.length)
        val header = ":global $pauseVar; :global $stateVar; :global $stepVar; :global $breakpointsVar; :global $sequenceVar; "
        return Result(header + result, lines)
    }

    private fun statements(file: RosFile): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        fun collect(scope: PsiElement) {
            for (child in scope.children) {
                if (child is PsiWhiteSpace || child is PsiComment) continue
                val type = child.node.elementType
                if (type == RosTypes.NEWLINE || type == RosTypes.SEMICOLON || type == RosTypes.LBRACE || type == RosTypes.RBRACE) continue
                if (child is dev.dybug.routeros.psi.RosStrayToken) continue
                result += child
            }
        }
        collect(file)
        PsiTreeUtil.findChildrenOfType(file, RosBlock::class.java).filter(::isCodeBlock).forEach(::collect)
        return result.sortedBy { it.textRange.startOffset }
    }

    /** `do={…}`, `:do {…}` and bare `{ … }` statements are code; `{1;2}` values and arrays are data. */
    private fun isCodeBlock(block: RosBlock): Boolean {
        val direct = when (val parent = block.parent) {
            is RosNamedArgument -> isCodeArgument(parent)
            is RosScriptCommand -> parent.firstChild?.text in setOf(":do", ":onerror", ":execute")
            // a bare block statement parses as a command whose only argument is the block
            is RosCommand -> parent.path == null && RosPsiUtil.arguments(parent).singleOrNull() === block
            else -> false
        }
        if (!direct) return false
        val outer = PsiTreeUtil.getParentOfType(block, RosBlock::class.java) ?: return true
        return isCodeBlock(outer)
    }

    /**
     * Blocks that run during this script. `in=` is code only for `:onerror` (an array for `:foreach`);
     * bodies of `:global` functions and `source=`/`on-event=` outlive the session and stay untouched.
     */
    private fun isCodeArgument(argument: RosNamedArgument): Boolean {
        val name = RosPsiUtil.argumentName(argument)
        val owner = argument.parent
        return when {
            owner is dev.dybug.routeros.psi.RosGlobalDecl -> false
            name == "in" -> owner is RosScriptCommand && owner.firstChild?.text == ":onerror"
            else -> name in CODE_ARGUMENTS
        }
    }

    private fun startsLine(text: String, offset: Int): Boolean {
        var i = offset - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
        // after a newline, at the start, or right after an opening brace of a block
        return i < 0 || text[i] == '\n' || text[i] == '\r' || text[i] == '{'
    }

    private fun variables(statement: PsiElement): List<String> =
        RosScopes.visibleAt(statement)
            .filter { it.kind != RosScopes.Kind.FUNCTION_GLOBAL && it.kind != RosScopes.Kind.FUNCTION_LOCAL }
            .filter { !(it.element.parent is RosDeclaration && (it.element.parent as RosDeclaration).name?.startsWith("RosDbg") == true) }
            .map { it.name }
            .filter { it.none { c -> c == '"' || c == '\\' } }
            .take(MAX_VARIABLES)

    private fun pausePoint(line: Int, variables: List<String>): String {
        val number = line + 1
        val values = variables.joinToString(";") { name ->
            val text = RosElementFactory.nameText(name)
            "\"${name.replace("$", "\\$")}\"=\$$text"
        }
        val state = "(\"{\\\"seq\\\":\" . \$$sequenceVar . \",\\\"line\\\":$number,\\\"vars\\\":\" . " +
            "[:serialize to=json value=({$values})] . \"}\")"
        // globals are redeclared inside the pause so that it also works within function bodies
        return ":if (\$$stepVar or [:find \$$breakpointsVar \",$number,\"] >= 0) do={ " +
            ":global $pauseVar; :global $stateVar; :global $sequenceVar; :set $sequenceVar (\$$sequenceVar + 1); " +
            ":set $stateVar $state; :set $pauseVar true; :while (\$$pauseVar) do={ :delay 100ms } }; "
    }

    private class LineIndex(text: String) {
        private val starts = buildList {
            add(0)
            text.forEachIndexed { index, c -> if (c == '\n') add(index + 1) }
        }

        fun lineOf(offset: Int): Int {
            val index = starts.binarySearch(offset)
            return if (index >= 0) index else -index - 2
        }
    }

    private companion object {
        const val MAX_VARIABLES = 50
        val CODE_ARGUMENTS = setOf("do", "else", "on-error", "command")
    }
}
