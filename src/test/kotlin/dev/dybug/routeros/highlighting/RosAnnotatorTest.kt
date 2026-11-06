package dev.dybug.routeros.highlighting

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.inspections.RosCommentAfterValueInspection
import dev.dybug.routeros.inspections.RosDeprecatedPropertyInspection
import dev.dybug.routeros.inspections.RosEmptyArrayInspection
import dev.dybug.routeros.inspections.RosInvalidVariableNameInspection
import dev.dybug.routeros.inspections.RosMissingDoBlockInspection
import dev.dybug.routeros.inspections.RosMissingMandatoryPropertyInspection
import dev.dybug.routeros.inspections.RosShadowedPropertyInspection
import dev.dybug.routeros.inspections.RosSpaceBeforeEqualsInspection
import dev.dybug.routeros.inspections.RosUndefinedVariableInspection
import dev.dybug.routeros.inspections.RosUnknownPropertyInspection
import dev.dybug.routeros.inspections.RosUnreachableCodeInspection
import dev.dybug.routeros.inspections.RosUnusedLocalInspection
import dev.dybug.routeros.inspections.RosValueTypeInspection
import java.io.File

class RosAnnotatorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(*ALL_INSPECTIONS.map { it.getDeclaredConstructor().newInstance() }.toTypedArray())
    }

    fun testUnknownPropertyAndBadValue() {
        myFixture.configureByText(
            "test.rsc",
            """
            /ip address
            add address=10.0.0.1/24 interface=ether1 <weak_warning descr="Unknown property 'colour' for this command">colour</weak_warning>=red
            /interface bridge
            add name=br vlan-filtering=<weak_warning descr="Value of 'vlan-filtering': expected yes or no">maybe</weak_warning>
            :put <error descr="Unmatched ')'">)</error>
            :put "<error descr="Invalid escape sequence '\4'">\4</error>a"
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testNoWarningsForPositionalAndUnknownMenus() {
        myFixture.configureByText(
            "test.rsc",
            "/system script run myscript\n/foo bar add anything=1\n/ip address print where interface=ether1\n/ip address print detail\n",
        )
        myFixture.checkHighlighting(true, false, true)
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
                .filter { it.severity.myVal >= HighlightSeverity.WEAK_WARNING.myVal && it.severity != HighlightSeverity.ERROR }
                .forEach { info ->
                    val generic = info.description.orEmpty()
                    counts.merge(generic, 1, Int::plus)
                }
        }
        File("build/reports").mkdirs()
        File("build/reports/annotator-noise.txt").writeText(
            "$files files, ${counts.values.sum()} warnings\n" +
                counts.entries.sortedByDescending { it.value }.take(200).joinToString("\n") { "${it.value}\t${it.key}" } + "\n",
        )
    }

    companion object {
        val ALL_INSPECTIONS: List<Class<out LocalInspectionTool>> = listOf(
            RosUndefinedVariableInspection::class.java, RosUnusedLocalInspection::class.java,
            RosUnknownPropertyInspection::class.java, RosValueTypeInspection::class.java,
            RosDeprecatedPropertyInspection::class.java, RosMissingMandatoryPropertyInspection::class.java,
            RosShadowedPropertyInspection::class.java, RosSpaceBeforeEqualsInspection::class.java,
            RosInvalidVariableNameInspection::class.java, RosEmptyArrayInspection::class.java,
            RosMissingDoBlockInspection::class.java, RosUnreachableCodeInspection::class.java,
            RosCommentAfterValueInspection::class.java,
        )
    }
}
