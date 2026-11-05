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
}
