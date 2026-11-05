package dev.dybug.routeros.structure

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiWhiteSpace
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosBracketCommand
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosTypes

/**
 * Folds blocks, multi-line bracket substitutions and commands joined with `\`, runs of comment
 * lines, and export sections (from one bare menu path to the next).
 */
class RosFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (root !is RosFile) return FoldingDescriptor.EMPTY_ARRAY
        val result = mutableListOf<FoldingDescriptor>()
        root.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is RosBlock, is RosBracketCommand -> addIfMultiline(element, element.textRange, result, document)
                    is RosCommand -> if (element.parent !is RosFile || !RosPsiUtil.isMenuContextLine(element)) {
                        addIfMultiline(element, element.textRange, result, document)
                    }
                }
                super.visitElement(element)
            }
        })
        addCommentRuns(root, document, result)
        addSections(root, document, result)
        return result.toTypedArray()
    }

    private fun addIfMultiline(
        element: PsiElement,
        range: TextRange,
        result: MutableList<FoldingDescriptor>,
        document: Document,
        placeholder: String? = null,
    ) {
        if (range.length < 2 || range.endOffset > document.textLength) return
        if (document.getLineNumber(range.startOffset) == document.getLineNumber(range.endOffset)) return
        result += if (placeholder == null) {
            FoldingDescriptor(element.node, range)
        } else {
            FoldingDescriptor(element.node, range, null, placeholder)
        }
    }

    private fun addCommentRuns(file: RosFile, document: Document, result: MutableList<FoldingDescriptor>) {
        var runStart: PsiComment? = null
        var runEnd: PsiComment? = null
        fun flush() {
            val start = runStart
            val end = runEnd
            if (start != null && end != null && start !== end) {
                addIfMultiline(
                    start,
                    TextRange(start.textRange.startOffset, end.textRange.endOffset),
                    result,
                    document,
                    start.text + " ...",
                )
            }
            runStart = null
            runEnd = null
        }
        var child = file.firstChild
        while (child != null) {
            when {
                child is PsiComment -> {
                    if (runStart == null) runStart = child
                    runEnd = child
                }
                child is PsiWhiteSpace || child.node.elementType == RosTypes.NEWLINE -> {
                    if (child.text.count { it == '\n' } > 1) flush()
                }
                else -> flush()
            }
            child = child.nextSibling
        }
        flush()
    }

    private fun addSections(file: RosFile, document: Document, result: MutableList<FoldingDescriptor>) {
        var sectionStart: RosCommand? = null
        var lastStatement: PsiElement? = null
        fun flush() {
            val start = sectionStart ?: return
            val end = lastStatement ?: return
            if (end !== start) {
                val range = TextRange(start.textRange.endOffset, end.textRange.endOffset)
                addIfMultiline(start, range, result, document, " ...")
            }
        }
        for (child in file.children) {
            if (child is RosCommand && RosPsiUtil.isMenuContextLine(child)) {
                flush()
                sectionStart = child
                lastStatement = child
            } else if (RosPsiUtil.isRelativeCommand(child)) {
                if (sectionStart != null) lastStatement = child
            } else if (child !is PsiComment && child.node.elementType != RosTypes.NEWLINE &&
                child.node.elementType != RosTypes.SEMICOLON && child !is PsiWhiteSpace
            ) {
                flush()
                sectionStart = null
                lastStatement = null
            }
        }
        flush()
    }

    override fun getPlaceholderText(node: ASTNode): String = when (node.elementType) {
        RosTypes.BLOCK -> "{...}"
        RosTypes.BRACKET_COMMAND -> "[...]"
        RosTypes.COMMAND -> node.text.substringBefore('\n').removeSuffix("\\").trimEnd() + " ..."
        else -> "..."
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
