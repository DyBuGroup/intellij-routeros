package dev.dybug.routeros

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosFileTypeTest : BasePlatformTestCase() {
    fun testRscIsRecognised() {
        val file = myFixture.configureByText("script.rsc", ":put \"hello\"\n")
        assertEquals(RosFileType, file.virtualFile.fileType)
    }
}
