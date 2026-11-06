package dev.dybug.routeros.formatting

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.application.options.CodeStyle
import dev.dybug.routeros.psi.RosTypes
import java.io.File

class RosFormatterTest : BasePlatformTestCase() {
    private fun format(text: String): String {
        myFixture.configureByText("test.rsc", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        return myFixture.editor.document.text
    }

    fun testIndentsBlocks() {
        assertEquals(
            ":if (\$a > 1) do={\n    :put 1\n    :foreach i in=\$b do={\n        :put \$i\n    }\n}\n",
            format(":if (\$a>1) do={\n:put 1\n  :foreach i in=\$b do={\n:put \$i\n}\n}\n"),
        )
    }

    fun testArgumentsAndEquals() {
        assertEquals(
            "/ip address add address=10.0.0.1/24 interface=ether1\n",
            format("/ip   address  add address=10.0.0.1/24    interface=ether1\n"),
        )
    }

    fun testExportStaysFlatAndContinuationsAreKept() {
        val export = "/ip firewall filter\nadd action=accept chain=input \\\n    comment=\"defconf\"\n/ip address\nadd address=1.2.3.4/24 interface=ether1\n"
        assertEquals(export, format(export))
    }

    fun testSectionIndentOption() {
        CodeStyle.getSettings(project).getCustomSettings(RosCodeStyleSettings::class.java).INDENT_SECTION_COMMANDS = true
        try {
            assertEquals(
                "/ip address\n    add address=1.2.3.4/24 interface=ether1\n:put 1\n",
                format("/ip address\nadd address=1.2.3.4/24 interface=ether1\n:put 1\n"),
            )
        } finally {
            CodeStyle.getSettings(project).getCustomSettings(RosCodeStyleSettings::class.java).INDENT_SECTION_COMMANDS = false
        }
    }

    fun testStringsAndCommentsUntouched() {
        val text = ":put \"a   b \$(\$x+1)\"   # keep   spacing\n"
        assertEquals(":put \"a   b \$(\$x + 1)\"   # keep   spacing\n", format(text))
    }

    /** Formatting must not change the token stream (apart from whitespace) and must be idempotent. */
    fun testCorpusRoundTrip() {
        val files = listOf(File("src/test/testData/corpus"), File("src/test/testData/lexer"))
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "rsc" }.toList() }
            .filter { !it.path.contains("intentional-errors") && !it.path.contains("missing-brace") && !it.path.contains("bad-") }
        val extra = System.getProperty("ros.extraCorpus")?.let(::File)
            ?.walkTopDown()?.filter { it.isFile && it.extension == "rsc" && it.length() < 200_000 }?.toList().orEmpty()
        for (file in files + extra) {
            val original = file.readText().replace("\r\n", "\n").replace('\r', '\n')
            myFixture.configureByText("test.rsc", original)
            if (file in extra && PsiTreeUtil.findChildOfType(myFixture.file, com.intellij.psi.PsiErrorElement::class.java) != null) continue
            val before = tokens(myFixture.file)
            val once = reformat()
            val after = tokens(myFixture.file)
            if (before != after) {
                val index = before.indices.firstOrNull { before[it] != after.getOrNull(it) } ?: before.size
                fail("tokens changed by formatting ${file.path} at $index: ${before.subList(maxOf(0, index - 5), minOf(before.size, index + 3))} -> ${after.subList(maxOf(0, index - 5), minOf(after.size, index + 3))}\n${myFixture.editor.document.text.take(2000)}")
            }
            assertEquals("formatting is not idempotent for ${file.path}", once, reformat())
        }
    }

    private fun reformat(): String {
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(myFixture.file) }
        return myFixture.editor.document.text
    }

    /** Leaf tokens without whitespace; runs of blank lines count as one line break (blank lines are not significant). */
    private fun tokens(file: PsiFile): List<String> {
        val result = mutableListOf<String>()
        for (leaf in PsiTreeUtil.collectElements(file) { it.firstChild == null && it !is PsiWhiteSpace }) {
            val newline = leaf.elementType == RosTypes.NEWLINE
            if (newline && (result.isEmpty() || result.last() == "\\n")) continue
            result += if (newline) "\\n" else "${leaf.elementType}:" + leaf.text
        }
        while (result.lastOrNull() == "\\n") result.removeAt(result.size - 1)
        return result
    }

    fun testWordOperatorsKeepSpacesWhenOperatorSpacingIsOff() {
        val custom = CodeStyle.getSettings(project).getCustomSettings(RosCodeStyleSettings::class.java)
        custom.SPACE_AROUND_OPERATORS = false
        try {
            assertEquals(":put (\$a and \$b || \$c)\n", format(":put (\$a and \$b || \$c)\n"))
            assertEquals(":put (1 . 2 . 3 . 4)\n", format(":put (1 . 2 . 3 . 4)\n"))
        } finally {
            custom.SPACE_AROUND_OPERATORS = true
        }
    }

    fun testWhitespaceAroundEqualsAndInPathsIsKept() {
        assertEquals("\$f a= b\n/import /flash/x.rsc\n", format("\$f a= b\n/import   /flash/x.rsc\n"))
    }
}
