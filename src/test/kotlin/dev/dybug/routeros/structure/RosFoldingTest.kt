package dev.dybug.routeros.structure

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosFoldingTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData/folding"

    fun testExport() {
        try {
            myFixture.testFolding("$testDataPath/export.rsc")
        } catch (e: com.intellij.platform.testFramework.core.FileComparisonFailedError) {
            throw AssertionError("actual:\n" + e.actualStringPresentation, e)
        }
    }
}
