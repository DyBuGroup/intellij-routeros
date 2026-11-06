package dev.dybug.routeros.documentation

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosDocumentationTest : BasePlatformTestCase() {
    private fun docAtCaret(text: String): String? {
        myFixture.configureByText("test.rsc", text)
        val provider = RosDocumentationProvider()
        val offset = myFixture.caretOffset
        val context = myFixture.file.findElementAt(offset)
        val element = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, context, offset)
        return provider.generateDoc(element, context)
    }

    fun testPropertyDoc() {
        val doc = docAtCaret("/ip address add inter<caret>face=ether1")
        assertNotNull(doc)
        assertTrue(doc!!, doc.contains("interface"))
    }

    fun testMenuDoc() {
        val doc = docAtCaret("/ip addr<caret>ess print")
        assertTrue(doc.orEmpty(), doc.orEmpty().contains("cli-reference/ip/address"))
    }

    fun testScriptCommandDoc() {
        val doc = docAtCaret(":fore<caret>ach i in=\$a do={}")
        assertTrue(doc.orEmpty(), doc.orEmpty().contains("each element"))
    }

    @Suppress("unused")
    private fun unused() = DocumentationManager::class
}
