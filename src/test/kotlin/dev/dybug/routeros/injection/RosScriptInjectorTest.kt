package dev.dybug.routeros.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.RosLanguage
import dev.dybug.routeros.psi.RosString

class RosScriptInjectorTest : BasePlatformTestCase() {
    fun testSourceOfSystemScriptIsInjected() {
        myFixture.configureByText("test.rsc", "/system script\nadd name=s source=\":local x \\\"a\\\"\\r\\n:put \\\$x\"\n")
        val injected = injectedText()
        assertEquals(":local x \"a\"\r\n:put \$x", injected)
    }

    fun testOtherStringsAreNotInjected() {
        myFixture.configureByText("test.rsc", "/ip address\nadd comment=\":put 1\"\n")
        assertNull(injectedText())
    }

    fun testInterpolatedStringsAreNotInjected() {
        myFixture.configureByText("test.rsc", "/system scheduler add on-event=\":put \$x\"\n")
        assertNull(injectedText())
    }

    fun testEscaperMapsOffsets() {
        myFixture.configureByText("test.rsc", ":execute script=\"a\\\\b\\41\"\n")
        assertEquals("a\\bA", injectedText())
    }

    private fun injectedText(): String? {
        val string = PsiTreeUtil.findChildrenOfType(myFixture.file, RosString::class.java).last()
        val manager = InjectedLanguageManager.getInstance(project)
        val files = manager.getInjectedPsiFiles(string) ?: return null
        val file = files.firstOrNull()?.first?.containingFile ?: return null
        assertEquals(RosLanguage, file.language)
        // injected leaves carry host (escaped) text; the decoded script is what was lexed and parsed
        return manager.getUnescapedText(file)
    }
}
