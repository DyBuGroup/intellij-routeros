package dev.dybug.routeros.psi

import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

object RosTokenSets {
    @JvmField
    val WHITESPACE: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    @JvmField
    val COMMENTS: TokenSet = TokenSet.create(RosTypes.COMMENT)

    @JvmField
    val STRING_PARTS: TokenSet = TokenSet.create(
        RosTypes.STRING_START,
        RosTypes.STRING_END,
        RosTypes.STRING_TEXT,
        RosTypes.STRING_ESCAPE,
        RosTypes.STRING_INVALID_ESCAPE,
    )

    @JvmField
    val SCRIPT_COMMANDS: TokenSet = TokenSet.create(
        RosTypes.SCRIPT_CMD,
        RosTypes.CMD_LOCAL,
        RosTypes.CMD_GLOBAL,
        RosTypes.CMD_SET,
    )

    @JvmField
    val KEYWORDS: TokenSet = TokenSet.create(
        RosTypes.WHERE_KW,
        RosTypes.AND_KW,
        RosTypes.OR_KW,
        RosTypes.IN_KW,
        RosTypes.NOT_KW,
        RosTypes.CONTROL_ARG,
    )

    @JvmField
    val OPERATORS: TokenSet = TokenSet.create(
        RosTypes.EQ, RosTypes.PLUS, RosTypes.MINUS, RosTypes.MUL, RosTypes.DIV, RosTypes.MOD,
        RosTypes.NEQ, RosTypes.LT, RosTypes.GT, RosTypes.LE, RosTypes.GE, RosTypes.NOT,
        RosTypes.ANDAND, RosTypes.OROR, RosTypes.BITAND, RosTypes.BITOR, RosTypes.XOR,
        RosTypes.TILDE, RosTypes.SHL, RosTypes.SHR, RosTypes.DOT, RosTypes.ARROW,
    )
}
