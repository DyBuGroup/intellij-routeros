package dev.dybug.routeros.parser

import com.intellij.testFramework.ParsingTestCase

class RosParserTest : ParsingTestCase("parser", "rsc", RosParserDefinition()) {
    override fun getTestDataPath(): String = "src/test/testData"

    override fun skipSpaces(): Boolean = true

    override fun includeRanges(): Boolean = false

    override fun getTestName(lowercaseFirstLetter: Boolean): String = super.getTestName(true)

    private fun doTest() = doTest(true, true)

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
    fun testBrokenLine() = doTest(true, false)
    fun testUnclosedBlock() = doTest(true, false)
    fun testBareScriptCommands() = doTest()
}
