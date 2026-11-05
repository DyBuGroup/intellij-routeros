package dev.dybug.routeros.lexer

import com.intellij.psi.TokenType
import com.intellij.testFramework.LexerTestCase
import dev.dybug.routeros.psi.RosTypes
import java.io.File

class RosLexerRobustnessTest : LexerTestCase() {
    override fun createLexer() = RosLexerAdapter()

    override fun getDirPath(): String = "src/test/testData/lexer"

    fun testCrLfAndCrAreNewlines() {
        for (eol in listOf("\r\n", "\r", "\n")) {
            val tokens = tokenize("/ip address${eol}print${eol}")
            assertEquals("eol=${eol.escape()}", 2, tokens.count { it.first == RosTypes.NEWLINE })
            assertTrue(tokens.none { it.first == TokenType.BAD_CHARACTER })
        }
    }

    fun testLineContinuationWithCrLf() {
        val tokens = tokenize("add a=1 \\\r\n    b=2\r\n")
        assertEquals(1, tokens.count { it.first == RosTypes.NEWLINE })
        assertEquals(listOf("a", "b"), tokens.filter { it.first == RosTypes.ARG_NAME }.map { it.second })
    }

    fun testRestartFromInitialStates() {
        for (file in testFiles()) {
            checkCorrectRestart(file.readText())
        }
    }

    fun testNoBadCharactersInTestData() {
        for (file in testFiles()) {
            assertNoBadCharacters(file)
        }
    }

    /** Set -Dros.extraCorpus=/path/to/dir to sweep private exports that must not be committed. */
    fun testNoBadCharactersInExtraCorpus() {
        val dir = System.getProperty("ros.extraCorpus")?.let(::File) ?: return
        dir.walkTopDown().filter { it.isFile && it.extension == "rsc" }.forEach(::assertNoBadCharacters)
    }

    fun testUnterminatedConstructsDoNotHang() {
        for (text in listOf("\"abc", ":put (1 + ", "[/ip address", "{ :put \"\$(", "\\", "\"\\", ":put \"\$[:len \\\"")) {
            tokenize(text)
        }
    }

    private fun testFiles(): List<File> {
        val roots = listOf(File(getDirPath()), File("src/test/testData/corpus"))
        return roots.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "rsc" }.toList() }
    }

    private fun assertNoBadCharacters(file: File) {
        val text = file.readText()
        val lexer = createLexer()
        lexer.start(text)
        while (lexer.tokenType != null) {
            if (lexer.tokenType == TokenType.BAD_CHARACTER) {
                val line = text.substring(0, lexer.tokenStart).count { it == '\n' } + 1
                fail("Bad character '${lexer.tokenText}' in ${file.path}:$line")
            }
            lexer.advance()
        }
    }

    private fun tokenize(text: String): List<Pair<Any, String>> {
        val lexer = createLexer()
        lexer.start(text)
        val result = mutableListOf<Pair<Any, String>>()
        var guard = 0
        while (lexer.tokenType != null) {
            result += lexer.tokenType!! to lexer.tokenText
            lexer.advance()
            check(++guard < 100_000) { "lexer does not advance" }
        }
        return result
    }

    private fun String.escape() = replace("\r", "\\r").replace("\n", "\\n")
}
