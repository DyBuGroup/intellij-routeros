package dev.dybug.routeros.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Wraps the generated JFlex lexer, which keeps a stack of states for nested brackets and strings.
 *
 * IntelliJ restarts incremental lexing only at tokens whose recorded state equals the initial
 * state, so the state reported for a token is 0 only when the token starts at top level with an
 * empty stack. Otherwise it is a non-zero fingerprint of the lexical state and the stack, which
 * lets the editor highlighter detect when re-lexing has converged.
 */
class RosLexerAdapter : LexerBase() {
    private val flex = _RouterOsLexer()
    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenType: IElementType? = null
    private var tokenStart = 0
    private var tokenEnd = 0
    private var state = 0
    private var located = false

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEnd = endOffset
        val lexicalState = if (initialState == 0) _RouterOsLexer.YYINITIAL else ((initialState and STATE_MASK) - 1).coerceAtLeast(0)
        flex.reset(buffer, startOffset, endOffset, lexicalState)
        flex.resetStack()
        tokenType = null
        tokenStart = startOffset
        tokenEnd = startOffset
        located = false
    }

    override fun getState(): Int {
        locateToken()
        return state
    }

    override fun getTokenType(): IElementType? {
        locateToken()
        return tokenType
    }

    override fun getTokenStart(): Int {
        locateToken()
        return tokenStart
    }

    override fun getTokenEnd(): Int {
        locateToken()
        return tokenEnd
    }

    override fun advance() {
        locateToken()
        located = false
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    private fun locateToken() {
        if (located) return
        located = true
        tokenStart = tokenEnd
        state = encodeState()
        tokenType = flex.advance()
        tokenEnd = flex.tokenEnd
    }

    private fun encodeState(): Int {
        val lexicalState = flex.yystate()
        if (lexicalState == _RouterOsLexer.YYINITIAL && flex.isStackEmpty) return 0
        return (lexicalState + 1) or (flex.stackHash() shl STATE_BITS)
    }

    private companion object {
        const val STATE_BITS = 5
        const val STATE_MASK = (1 shl STATE_BITS) - 1
    }
}
