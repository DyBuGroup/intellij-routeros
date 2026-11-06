package dev.dybug.routeros.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosElementFactory
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosGlobalDecl
import dev.dybug.routeros.psi.RosLocalDecl
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosScriptCommand
import dev.dybug.routeros.psi.RosSetStatement
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.references.RosNamesValidator

/** `name =value` — RouterOS does not allow whitespace between a parameter and `=`. */
class RosSpaceBeforeEqualsInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosNamedArgument) return
        val key = element.firstChild ?: return
        val space = key.nextSibling as? PsiWhiteSpace ?: return
        if (space.nextSibling?.elementType != RosTypes.EQ) return
        holder.registerProblem(
            element, RosBundle.message("inspection.space.before.equals"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            TextRange.from(space.startOffsetInParent, space.textLength),
            RosQuickFixes.ReplaceText("fix.remove.space", ""),
        )
    }
}

/** Unquoted variable names may contain letters and digits only; `and`, `or`, `in` are reserved. */
class RosInvalidVariableNameInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosLocalDecl && element !is RosGlobalDecl) return
        val nameElement = RosPsiUtil.declaredNameElement(element) ?: return
        if (nameElement.elementType != RosTypes.WORD) return
        val name = nameElement.text
        when {
            name in RosNamesValidator.KEYWORDS ->
                holder.registerProblem(nameElement, RosBundle.message("inspection.reserved.name", name), ProblemHighlightType.GENERIC_ERROR)
            !RosElementFactory.isPlainName(name) ->
                holder.registerProblem(
                    nameElement, RosBundle.message("inspection.invalid.name", name), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    RosQuickFixes.ReplaceText("fix.quote.name", RosElementFactory.nameText(name)),
                )
        }
    }
}

/** Bare `{}` is not a valid empty array; `({})` and `[:toarray ""]` are. Empty blocks (`do={}`) are fine. */
class RosEmptyArrayInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosBlock) return
        if (element.children.any { it !is PsiWhiteSpace && it.elementType != RosTypes.NEWLINE }) return
        val parent = element.parent
        val isValue = when (parent) {
            is RosLocalDecl, is RosGlobalDecl, is RosSetStatement -> true
            is RosNamedArgument -> RosPsiUtil.argumentName(parent) !in BLOCK_ARGUMENTS
            // ({}) is the accepted idiom for an empty array
            else -> false
        }
        if (!isValue) return
        holder.registerProblem(
            element, RosBundle.message("inspection.empty.array"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            RosQuickFixes.ReplaceText("fix.toarray", "[:toarray \"\"]"),
        )
    }

    private companion object {
        val BLOCK_ARGUMENTS = setOf("do", "else", "on-error", "in", "command", "script", "source", "on-event")
    }
}

/** `:if`, `:while`, `:for`, `:foreach` need a `do={…}` block. */
class RosMissingDoBlockInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosScriptCommand) return
        val keyword = element.firstChild ?: return
        if (keyword.text !in LOOPS) return
        if (RosPsiUtil.namedArguments(element).any { RosPsiUtil.argumentName(it) == "do" }) return
        holder.registerProblem(keyword, RosBundle.message("inspection.missing.do", keyword.text), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }

    private companion object {
        val LOOPS = setOf(":if", ":while", ":for", ":foreach")
    }
}

/** Statements after `:return` or `:error` in the same block never run. */
class RosUnreachableCodeInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosScriptCommand) return
        val keyword = element.firstChild?.text ?: return
        if (keyword != ":return" && keyword != ":error") return
        if (element.parent !is RosBlock && element.parent !is RosFile) return
        var next = element.nextSibling
        var first: PsiElement? = null
        var last: PsiElement? = null
        while (next != null) {
            if (next.elementType == RosTypes.RBRACE) break
            if (next !is PsiWhiteSpace && next !is PsiComment && next.elementType != RosTypes.NEWLINE && next.elementType != RosTypes.SEMICOLON) {
                if (first == null) first = next
                last = next
            }
            next = next.nextSibling
        }
        if (first == null || last == null) return
        holder.registerProblem(
            holder.manager.createProblemDescriptor(
                first, last, RosBundle.message("inspection.unreachable", keyword), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                holder.isOnTheFly, RosQuickFixes.Delete("fix.remove.unreachable"),
            ),
        )
    }
}

/** On the router, `#` starts a comment only at the start of a line or after `;` (verified on RouterOS 7.25). */
class RosCommentAfterValueInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is PsiComment) return
        val previous = PsiTreeUtil.prevLeaf(element)
        val beforeSpace = if (previous is PsiWhiteSpace) PsiTreeUtil.prevLeaf(previous) else previous
        if (beforeSpace == null || previous !is PsiWhiteSpace) return
        val type = beforeSpace.elementType
        if (type == RosTypes.NEWLINE || type == RosTypes.SEMICOLON || type == RosTypes.LBRACE || type == RosTypes.RBRACE || beforeSpace is PsiComment) return
        if (previous.text.contains('\n') || previous.text.contains('\r')) return
        holder.registerProblem(
            element, RosBundle.message("inspection.comment.after.value"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            TextRange(0, 1), RosQuickFixes.ReplaceText("fix.insert.semicolon", "; #"),
        )
    }
}
