package dev.dybug.routeros.device

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.device.debug.RosInstrumenter
import dev.dybug.routeros.psi.RosFile

class RosInstrumenterTest : BasePlatformTestCase() {
    private val script = """
        :local a 1
        :local arr {1;2;3}
        :global f do={
            :local inner (${'$'}1 * 2)
            :return ${'$'}inner
        }
        :foreach i in=${'$'}arr do={
            :set a (${'$'}a + ${'$'}i)
        }
        :put [${'$'}f ${'$'}a]
    """.trimIndent() + "\n"

    fun testKeepsLinesAndParses() {
        val file = myFixture.configureByText("s.rsc", script) as RosFile
        val result = RosInstrumenter("t1").instrument(file)
        assertEquals(script.lines().size, result.text.lines().size)
        assertEquals(setOf(0, 1, 2, 3, 4, 6, 7, 9), result.pauseLines)
        val instrumented = myFixture.configureByText("i.rsc", result.text)
        assertNull(result.text, PsiTreeUtil.findChildOfType(instrumented, PsiErrorElement::class.java))
    }

    fun testArraysAreNotInstrumented() {
        val file = myFixture.configureByText("s.rsc", ":local m {\n  a=1;\n  b=2\n}\n:put \$m\n") as RosFile
        val result = RosInstrumenter("t2").instrument(file)
        assertEquals(setOf(0, 4), result.pauseLines)
    }

    fun testCapturesVisibleVariables() {
        val file = myFixture.configureByText("s.rsc", ":local a 1\n:local \"b c\" 2\n:put \$a\n") as RosFile
        val line = RosInstrumenter("t3").instrument(file).text.lines()[2]
        assertTrue(line, line.contains("\"a\"=\$a") && line.contains("\"b c\"=\$\"b c\""))
    }
}
