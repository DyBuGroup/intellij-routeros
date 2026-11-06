package dev.dybug.routeros.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class RosAnnotatorTest : BasePlatformTestCase() {
    fun testUnknownPropertyAndBadValue() {
        myFixture.configureByText(
            "test.rsc",
            """
            /ip address
            add address=10.0.0.1/24 interface=ether1 <weak_warning descr="Unknown property 'colour' for this command">colour</weak_warning>=red
            /interface bridge
            add name=br vlan-filtering=<weak_warning descr="Value of 'vlan-filtering': expected yes or no">maybe</weak_warning>
            :put <error descr="Unmatched ')'">)</error>
            """.trimIndent(),
        )
        myFixture.checkHighlighting(false, false, true)
    }

    fun testNoWarningsForPositionalAndUnknownMenus() {
        myFixture.configureByText(
            "test.rsc",
            "/system script run myscript\n/foo bar add anything=1\n/ip address print where interface=ether1\n",
        )
        myFixture.checkHighlighting(false, false, true)
    }

    /** Pass -Pros.extraCorpus=/dir to count warnings on private exports; the report lists the most frequent. */
    fun testWarningNoiseOnExtraCorpus() {
        val dir = System.getProperty("ros.extraCorpus")?.let(::File) ?: return
        val counts = mutableMapOf<String, Int>()
        var files = 0
        dir.walkTopDown().filter { it.isFile && it.extension == "rsc" && it.length() < 300_000 }.forEach { file ->
            files++
            myFixture.configureByText("corpus.rsc", file.readText())
            myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING)
                .filter { it.severity == HighlightSeverity.WEAK_WARNING }
                .forEach { info ->
                    val leaf = myFixture.file.findElementAt(info.startOffset)
                    val command = com.intellij.psi.util.PsiTreeUtil.getParentOfType(leaf, dev.dybug.routeros.psi.RosCommand::class.java)
                    val path = command?.let { dev.dybug.routeros.psi.RosPsiUtil.resolvedPath(it).joinToString("/") }
                    counts.merge("${info.description} @ /$path", 1, Int::plus)
                }
        }
        File("build/reports").mkdirs()
        File("build/reports/annotator-noise.txt").writeText(
            "$files files, ${counts.values.sum()} weak warnings\n" +
                counts.entries.sortedByDescending { it.value }.take(200).joinToString("\n") { "${it.value}\t${it.key}" } + "\n",
        )
    }
}
