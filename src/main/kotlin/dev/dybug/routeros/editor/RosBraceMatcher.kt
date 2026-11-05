package dev.dybug.routeros.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import dev.dybug.routeros.psi.RosTypes

class RosBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    private companion object {
        val PAIRS = arrayOf(
            BracePair(RosTypes.LBRACE, RosTypes.RBRACE, true),
            BracePair(RosTypes.LBRACKET, RosTypes.RBRACKET, false),
            BracePair(RosTypes.LPAREN, RosTypes.RPAREN, false),
        )
    }
}
