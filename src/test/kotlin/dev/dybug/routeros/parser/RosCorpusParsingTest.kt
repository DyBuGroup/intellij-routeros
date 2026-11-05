package dev.dybug.routeros.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.psi.RosStrayToken
import java.io.File

/**
 * Every corpus file must parse without error elements. Files that are invalid on purpose are
 * listed in `corpus/allow-errors.txt`. Pass -Pros.extraCorpus=/dir to sweep files that must
 * not be committed (licence or privacy); problems there are written to a report.
 */
class RosCorpusParsingTest : BasePlatformTestCase() {
    private val corpus = File("src/test/testData/corpus")

    fun testCorpusParsesWithoutErrors() {
        val allowed = File(corpus, "allow-errors.txt").takeIf { it.isFile }?.readLines()
            ?.map { it.substringBefore('#').trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        val failures = corpus.walkTopDown().filter { it.isFile && it.extension == "rsc" }
            .filter { it.relativeTo(corpus).invariantSeparatorsPath !in allowed }
            .mapNotNull { file -> firstProblem(file)?.let { "${file.relativeTo(corpus)}: $it" } }
            .toList()
        assertEmpty(failures)
    }

    fun testExtraCorpus() {
        val dir = System.getProperty("ros.extraCorpus")?.let(::File) ?: return
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "rsc" }.toList()
        val failures = files.mapNotNull { file -> firstProblem(file)?.let { "${file.path}: $it" } }
        File("build/reports").mkdirs()
        File("build/reports/extra-corpus.txt").writeText(
            "${failures.size} of ${files.size} files have parse problems\n" + failures.joinToString("\n") + "\n",
        )
    }

    private fun firstProblem(file: File): String? {
        val text = file.readText()
        val psi = myFixture.configureByText("corpus.rsc", text)
        val error = PsiTreeUtil.findChildOfType(psi, PsiErrorElement::class.java)
            ?: PsiTreeUtil.findChildOfType(psi, RosStrayToken::class.java)
            ?: return null
        val offset = error.textRange.startOffset
        val line = text.substring(0, offset).count { it == '\n' } + 1
        val message = (error as? PsiErrorElement)?.errorDescription ?: "unmatched '${error.text}'"
        val lineText = text.lines().getOrNull(line - 1)?.trim()?.take(120)
        return "line $line: $message | $lineText"
    }
}
