package dev.dybug.routeros.references

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.psi.RosDeclaration

class RosReferencesTest : BasePlatformTestCase() {
    private fun resolveAtCaret(): PsiElement? = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()

    fun testLocalResolves() {
        myFixture.configureByText("a.rsc", ":local count 1\n:put \$co<caret>unt\n")
        assertEquals("count", (resolveAtCaret() as RosDeclaration).name)
    }

    fun testLocalsAreBlockScoped() {
        myFixture.configureByText("a.rsc", "{ :local inner 1 }\n:put \$in<caret>ner\n")
        assertNull(resolveAtCaret())
    }

    fun testLoopVariable() {
        myFixture.configureByText("a.rsc", ":foreach item in=\$list do={ :put \$it<caret>em }\n")
        assertEquals("item", resolveAtCaret()?.text)
    }

    fun testQuotedNameAndInterpolation() {
        myFixture.configureByText("a.rsc", ":local \"my-var\" 1\n:put (\$\"my-<caret>var\" . \"\$x\")\n")
        assertEquals("my-var", (resolveAtCaret() as? RosDeclaration)?.name)
    }

    fun testCrossFileGlobal() {
        myFixture.addFileToProject("lib.rsc", ":global greet do={ :return \"hi\" }\n")
        myFixture.configureByText("main.rsc", ":global greet\n:put [\$gr<caret>eet]\n")
        assertEquals("greet", (resolveAtCaret() as RosDeclaration).name)
        myFixture.configureByText("other.rsc", ":put [\$gr<caret>eet]\n")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset) as com.intellij.psi.PsiPolyVariantReference
        val files = reference.multiResolve(false).mapNotNull { it.element?.containingFile?.name }
        assertTrue(files.toString(), "lib.rsc" in files)
    }

    fun testRenameLocal() {
        myFixture.configureByText("a.rsc", ":local co<caret>unt 1\n:set count 2\n:put \"n=\$count\"\n:put (\$count + 1)\n")
        myFixture.renameElementAtCaret("total")
        myFixture.checkResult(":local total 1\n:set total 2\n:put \"n=\$total\"\n:put (\$total + 1)\n")
    }

    fun testRenameToQuotedName() {
        myFixture.configureByText("a.rsc", ":local a<caret>b 1\n:put \$ab\n")
        myFixture.renameElementAtCaret("my-name")
        myFixture.checkResult(":local \"my-name\" 1\n:put \$\"my-name\"\n")
    }

    fun testFindUsages() {
        myFixture.configureByText("a.rsc", ":global x<caret> 1\n:put \$x\n:set x 2\n")
        assertEquals(2, myFixture.findUsages(myFixture.elementAtCaret).size)
    }

    fun testGotoSymbol() {
        myFixture.addFileToProject("lib.rsc", ":global helperFunction do={ :return 1 }\n")
        val contributor = RosGotoSymbolContributor()
        val names = mutableListOf<String>()
        contributor.processNames({ names += it; true }, com.intellij.psi.search.GlobalSearchScope.projectScope(project), null)
        assertTrue(names.toString(), "helperFunction" in names)
    }

    fun testRenameInsideStringUsesInterpolation() {
        myFixture.configureByText("a.rsc", ":local a<caret>b 1\n:put \"n=\$ab\"\n")
        myFixture.renameElementAtCaret("my-name")
        myFixture.checkResult(":local \"my-name\" 1\n:put \"n=\$(\$\\\"my-name\\\")\"\n")
    }

    fun testEscapedNamesAreCanonical() {
        myFixture.configureByText("a.rsc", ":local \"a\\62\" 1\n:put \$a<caret>b\n")
        assertEquals("ab", (resolveAtCaret() as? RosDeclaration)?.name)
    }

    fun testLoopVariableOnlyInBody() {
        myFixture.configureByText("a.rsc", ":local item {1;2}\n:foreach item in=\$it<caret>em do={ :put \$item }\n")
        assertTrue(resolveAtCaret() is RosDeclaration)
    }

    fun testRenameGlobalAcrossFiles() {
        myFixture.addFileToProject("consumer.rsc", ":global counter\n:put \$counter\n")
        myFixture.configureByText("lib.rsc", ":global coun<caret>ter 0\n")
        myFixture.renameElementAtCaret("total")
        myFixture.checkResult(":global total 0\n")
        val consumer = myFixture.findFileInTempDir("consumer.rsc")
        assertEquals(":global total\n:put \$total\n", com.intellij.psi.PsiManager.getInstance(project).findFile(consumer)!!.text)
    }
}
