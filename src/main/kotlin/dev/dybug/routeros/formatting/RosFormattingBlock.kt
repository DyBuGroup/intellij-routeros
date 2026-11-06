package dev.dybug.routeros.formatting

import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import dev.dybug.routeros.RosLanguage
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosTokenSets
import dev.dybug.routeros.psi.RosTypes

class RosFormattingBlock(
    node: ASTNode,
    private val indent: Indent?,
    private val settings: CodeStyleSettings,
    private val spacingBuilder: SpacingBuilder,
) : AbstractBlock(node, null, null) {
    private val custom = settings.getCustomSettings(RosCodeStyleSettings::class.java)
    private val keepBlank = settings.getCommonSettings(RosLanguage).KEEP_BLANK_LINES_IN_CODE

    override fun buildChildren(): List<Block> {
        val result = mutableListOf<Block>()
        var inSection = false
        var child = myNode.firstChildNode
        while (child != null) {
            val type = child.elementType
            if (type != TokenType.WHITE_SPACE && type != RosTypes.NEWLINE && child.textLength > 0) {
                val childIndent = childIndent(child, inSection)
                result += RosFormattingBlock(child, childIndent, settings, spacingBuilder)
            }
            if (myNode.psi is dev.dybug.routeros.psi.RosFile) {
                val psi = child.psi
                if (psi is RosCommand && RosPsiUtil.isMenuContextLine(psi)) {
                    inSection = true
                } else if (psi != null && type != TokenType.WHITE_SPACE && type != RosTypes.NEWLINE &&
                    type != RosTypes.SEMICOLON && type !in RosTokenSets.COMMENTS && !RosPsiUtil.isRelativeCommand(psi)
                ) {
                    inSection = false
                }
            }
            child = child.treeNext
        }
        return result
    }

    private fun childIndent(child: ASTNode, inSection: Boolean): Indent {
        val parentType = myNode.elementType
        val type = child.elementType
        return when {
            parentType == RosTypes.BLOCK -> if (type == RosTypes.LBRACE || type == RosTypes.RBRACE) Indent.getNoneIndent() else Indent.getNormalIndent()
            parentType == RosTypes.BRACKET_COMMAND || parentType == RosTypes.PAREN_EXPR ->
                if (type == RosTypes.LBRACKET || type == RosTypes.RBRACKET || type == RosTypes.LPAREN || type == RosTypes.RPAREN) {
                    Indent.getNoneIndent()
                } else {
                    Indent.getContinuationWithoutFirstIndent()
                }
            myNode.psi is dev.dybug.routeros.psi.RosFile ->
                if (custom.INDENT_SECTION_COMMANDS && inSection && child.psi?.let(RosPsiUtil::isRelativeCommand) == true) {
                    Indent.getNormalIndent()
                } else {
                    Indent.getNoneIndent()
                }
            else -> Indent.getContinuationWithoutFirstIndent()
        }
    }

    override fun getIndent(): Indent? = indent

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (child1 !is RosFormattingBlock || child2 !is RosFormattingBlock) return null
        val left = child1.node
        val right = child2.node
        val parentType = myNode.elementType
        // gaps holding a `\` continuation (and the indentation of the continued line) stay untouched
        if (gapHasContinuation(left, right)) return Spacing.getReadOnlySpacing()
        // never split glued tokens (`$ip/24`, `<where>`): adjacency is significant to the lexer.
        // Only expression operators and braces may gain a space.
        if (left.textRange.endOffset == right.textRange.startOffset && !mayInsertSpace(left, right)) {
            return Spacing.createSpacing(0, 0, 0, true, keepBlank)
        }
        spacingBuilder.getSpacing(this, child1, child2)?.let { return it }
        return when {
            left.elementType == RosTypes.EQ || right.elementType == RosTypes.EQ ->
                if (parentType == RosTypes.NAMED_ARGUMENT) Spacing.createSpacing(0, 0, 0, true, keepBlank) else keep()
            parentType == RosTypes.PATH -> when {
                left.elementType == RosTypes.SLASH || right.elementType == RosTypes.SLASH -> Spacing.createSpacing(0, 0, 0, true, keepBlank)
                else -> Spacing.createSpacing(1, 1, 0, true, keepBlank)
            }
            parentType == RosTypes.BLOCK && left.elementType == RosTypes.LBRACE && right.elementType == RosTypes.RBRACE ->
                Spacing.createSpacing(0, 1, 0, true, keepBlank)
            parentType == RosTypes.BLOCK && (left.elementType == RosTypes.LBRACE || right.elementType == RosTypes.RBRACE) -> {
                val spaces = if (custom.SPACE_WITHIN_BRACES) 1 else 0
                Spacing.createSpacing(spaces, spaces, 0, true, keepBlank)
            }
            parentType == RosTypes.VARIABLE || parentType == RosTypes.VARIABLE_INDEX || parentType == RosTypes.STRING ||
                parentType == RosTypes.INTERPOLATION -> Spacing.createSpacing(0, 0, 0, true, keepBlank)
            isArgumentList(parentType) -> Spacing.createSpacing(1, 1, 0, true, keepBlank)
            else -> keep()
        }
    }

    private fun mayInsertSpace(left: ASTNode, right: ASTNode): Boolean {
        val parentType = myNode.elementType
        if (parentType == RosTypes.BLOCK) return left.elementType == RosTypes.LBRACE || right.elementType == RosTypes.RBRACE
        return parentType in EXPRESSIONS && (left.elementType in OPERATORS || right.elementType in OPERATORS)
    }

    private fun gapHasContinuation(left: ASTNode, right: ASTNode): Boolean {
        val text = myNode.psi.containingFile?.viewProvider?.contents ?: return false
        val start = left.textRange.endOffset
        val end = right.textRange.startOffset
        if (start >= end || end > text.length) return false
        for (i in start until end) if (text[i] == '\\') return true
        return false
    }

    /** Leaves the gap alone except that line breaks are always kept. */
    private fun keep(): Spacing = Spacing.createSpacing(0, Int.MAX_VALUE, 0, true, keepBlank)

    private fun isArgumentList(type: com.intellij.psi.tree.IElementType): Boolean =
        type == RosTypes.COMMAND || type == RosTypes.SCRIPT_COMMAND || type == RosTypes.LOCAL_DECL ||
            type == RosTypes.GLOBAL_DECL || type == RosTypes.SET_STATEMENT || type == RosTypes.FUNCTION_CALL ||
            type == RosTypes.WHERE_CLAUSE

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = when (myNode.elementType) {
        RosTypes.BLOCK -> ChildAttributes(Indent.getNormalIndent(), null)
        RosTypes.BRACKET_COMMAND, RosTypes.PAREN_EXPR -> ChildAttributes(Indent.getContinuationWithoutFirstIndent(), null)
        else -> ChildAttributes(Indent.getNoneIndent(), null)
    }

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    private companion object {
        val OPERATORS = com.intellij.psi.tree.TokenSet.create(
            RosTypes.PLUS, RosTypes.MINUS, RosTypes.MUL, RosTypes.DIV, RosTypes.MOD, RosTypes.NEQ, RosTypes.LT, RosTypes.GT,
            RosTypes.LE, RosTypes.GE, RosTypes.ANDAND, RosTypes.OROR, RosTypes.BITAND, RosTypes.BITOR, RosTypes.XOR,
            RosTypes.SHL, RosTypes.SHR, RosTypes.DOT, RosTypes.APPLY, RosTypes.EQ,
        )
        val EXPRESSIONS = com.intellij.psi.tree.TokenSet.create(
            RosTypes.OR_EXPR, RosTypes.AND_EXPR, RosTypes.MATCH_EXPR, RosTypes.COMPARE_EXPR, RosTypes.APPLY_EXPR,
            RosTypes.CONCAT_EXPR, RosTypes.BIT_OR_EXPR, RosTypes.BIT_XOR_EXPR, RosTypes.BIT_AND_EXPR,
            RosTypes.SHIFT_EXPR, RosTypes.ADD_EXPR, RosTypes.MUL_EXPR,
        )
    }
}
