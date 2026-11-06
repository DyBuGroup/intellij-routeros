package dev.dybug.routeros.formatting

import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosTemplatesTest : BasePlatformTestCase() {
    fun testLiveTemplatesAreLoaded() {
        val names = TemplateSettings.getInstance().templates.filter { it.groupName == "RouterOS" }.map { it.key }
        assertTrue(names.toString(), names.containsAll(listOf("foreach", "if", "onerror", "fw-drop-invalid", "print-where")))
    }

    fun testForeachTemplateExpands() {
        myFixture.configureByText("test.rsc", "foreach<caret>")
        myFixture.performEditorAction("ExpandLiveTemplateByTab")
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.startsWith(":foreach"))
    }

    fun testFileTemplateExists() {
        val template = com.intellij.ide.fileTemplates.FileTemplateManager.getInstance(project).getInternalTemplate("RouterOS Script")
        assertTrue(template.text.contains("Policies"))
    }
}
