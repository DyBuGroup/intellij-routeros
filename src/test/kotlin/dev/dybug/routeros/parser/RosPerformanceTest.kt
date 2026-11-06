package dev.dybug.routeros.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosPerformanceTest : BasePlatformTestCase() {
    /** A ~1 MB synthetic export must lex and parse well within a generous budget. */
    fun testLargeExportParsesQuickly() {
        val text = buildString {
            var i = 0
            while (length < 1_000_000) {
                if (i % 200 == 0) append("/ip firewall filter\n")
                append("add action=accept chain=forward comment=\"rule $i\" dst-address=10.0.${i % 250}.0/24 \\\n")
                append("    in-interface-list=LAN protocol=tcp dst-port=${1000 + i % 5000}\n")
                i++
            }
        }
        val started = System.nanoTime()
        val file = myFixture.configureByText("big.rsc", text)
        file.node.lastChildNode
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
        assertTrue("parsing 1 MB took $elapsedMs ms", elapsedMs < 20_000)
    }

    /** Nested string interpolation must not cause exponential re-parsing. */
    fun testNestedInterpolationIsLinear() {
        var text = "\"x\""
        repeat(25) { text = "\"\$[:put $text]\"" }
        val started = System.nanoTime()
        val file = myFixture.configureByText("nested.rsc", ":put $text\n")
        file.node.lastChildNode
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("nested interpolation took $elapsedMs ms", elapsedMs < 5_000)
    }

    fun testMissingBracketDoesNotSwallowFollowingStatements() {
        val file = myFixture.configureByText("broken.rsc", ":put ([find)\n/ip address print\n")
        val commands = PsiTreeUtil.getChildrenOfTypeAsList(file, dev.dybug.routeros.psi.RosCommand::class.java)
        assertEquals(1, commands.size)
        assertEquals("/ip address print", commands.single().text)
    }

    /** Highlighting with variable inspections must stay near-linear in the number of variables. */
    fun testManyVariablesHighlightQuickly() {
        myFixture.enableInspections(
            dev.dybug.routeros.inspections.RosUndefinedVariableInspection(),
            dev.dybug.routeros.inspections.RosUnusedLocalInspection(),
        )
        val text = buildString {
            for (i in 0 until 3000) append(":local v$i $i\n:put (\$v$i + \$missing$i)\n")
        }
        myFixture.configureByText("vars.rsc", text)
        val started = System.nanoTime()
        myFixture.doHighlighting()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("highlighting took $elapsedMs ms", elapsedMs < 30_000)
    }
}
