package dev.dybug.routeros.formatting

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.tree.TokenSet
import dev.dybug.routeros.RosLanguage
import dev.dybug.routeros.psi.RosTypes

/**
 * Formats RouterOS files conservatively: indentation inside `{ … }`, single spaces between
 * arguments, no spaces around `=`, optional spaces around operators. Line breaks are never
 * removed (statements end at newlines), and gaps containing a `\` continuation are left as is.
 * A document-based model is used because newline tokens are significant and not PSI whitespace.
 */
class RosFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings
        val file = formattingContext.containingFile
        val root = RosFormattingBlock(file.node, null, settings, spacingBuilder(settings))
        return DocumentBasedFormattingModel(root, file.project, settings, file.fileType, file)
    }

    companion object {
        private val BINARY_OPERATORS = TokenSet.create(
            RosTypes.PLUS, RosTypes.MUL, RosTypes.DIV, RosTypes.MOD, RosTypes.NEQ, RosTypes.LT, RosTypes.GT, RosTypes.LE,
            RosTypes.GE, RosTypes.ANDAND, RosTypes.OROR, RosTypes.BITAND,
            RosTypes.BITOR, RosTypes.XOR, RosTypes.SHL, RosTypes.SHR, RosTypes.DOT, RosTypes.APPLY,
        )

        /** Word operators must stay separated from their operands whatever the settings say. */
        private val WORD_OPERATORS = TokenSet.create(RosTypes.AND_KW, RosTypes.OR_KW, RosTypes.IN_KW, RosTypes.NOT_KW)

        fun spacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
            val custom = settings.getCustomSettings(RosCodeStyleSettings::class.java)
            val common = settings.getCommonSettings(RosLanguage)
            val keepBlank = common.KEEP_BLANK_LINES_IN_CODE
            // without the option existing spaces are kept: removing them could merge tokens
            // (`1 . 2 . 3 . 4` would become the IP address `1.2.3.4`)
            val operatorSpaces = if (custom.SPACE_AROUND_OPERATORS) 1 else 0
            val operatorMax = if (custom.SPACE_AROUND_OPERATORS) 1 else Int.MAX_VALUE
            return SpacingBuilder(settings, RosLanguage)
                .around(WORD_OPERATORS).spacing(1, Int.MAX_VALUE, 0, true, keepBlank)
                .around(RosTypes.ARROW).spacing(0, 0, 0, true, keepBlank)
                .before(RosTypes.SEMICOLON).spacing(0, 0, 0, true, keepBlank)
                .after(RosTypes.SEMICOLON).spacing(1, 1, 0, true, keepBlank)
                .before(RosTypes.COMMENT).spacing(1, Int.MAX_VALUE, 0, true, keepBlank)
                .aroundInside(BINARY_OPERATORS, RosTypes.PAREN_EXPR).spacing(operatorSpaces, operatorMax, 0, true, keepBlank)
                .aroundInside(BINARY_OPERATORS, TokenSet.create(
                    RosTypes.OR_EXPR, RosTypes.AND_EXPR, RosTypes.MATCH_EXPR, RosTypes.COMPARE_EXPR, RosTypes.APPLY_EXPR,
                    RosTypes.CONCAT_EXPR, RosTypes.BIT_OR_EXPR, RosTypes.BIT_XOR_EXPR, RosTypes.BIT_AND_EXPR,
                    RosTypes.SHIFT_EXPR, RosTypes.ADD_EXPR, RosTypes.MUL_EXPR,
                )).spacing(operatorSpaces, operatorMax, 0, true, keepBlank)
        }
    }
}
