package dev.dybug.routeros.lexer

import com.intellij.lexer.Lexer
import com.intellij.testFramework.LexerTestCase
import java.io.File

class RosLexerTest : LexerTestCase() {
    override fun createLexer(): Lexer = RosLexerAdapter()

    override fun getDirPath(): String = "src/test/testData/lexer"

    override fun getPathToTestDataFile(extension: String): String =
        File(getDirPath(), getTestName(true) + extension).absolutePath

    private fun doTest() = doFileTest("rsc")

    fun testExportBasic() = doTest()
    fun testDivisionVsPath() = doTest()
    fun testSlashPath() = doTest()
    fun testBracketFind() = doTest()
    fun testForeach() = doTest()
    fun testArrays() = doTest()
    fun testFunction() = doTest()
    fun testInterpolation() = doTest()
    fun testContinuation() = doTest()
    fun testIfElse() = doTest()
    fun testWhereClause() = doTest()
    fun testErrorHandling() = doTest()
    fun testFetchUrl() = doTest()
    fun testArrayIndex() = doTest()
    fun testDelay() = doTest()
    fun testRelativePath() = doTest()
    fun testEscapes() = doTest()
    fun testCrlf() = doTest()
    fun testScriptBlock() = doTest()
    fun testMonitorDo() = doTest()
    fun testForLoop() = doTest()
    fun testExprLiterals() = doTest()
    fun testNumericArgs() = doTest()
}
