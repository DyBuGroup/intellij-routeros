package dev.dybug.routeros.oracle

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.UsefulTestCase
import dev.dybug.routeros.lexer.RosLexerAdapter
import dev.dybug.routeros.psi.RosTokenSets
import dev.dybug.routeros.psi.RosTypes
import java.io.File

/**
 * Compares our token classes with RouterOS's own highlighter (captured from a router and stored in
 * `testData/oracle`). The comparison is coarse and diagnostic: it fails only when the mismatch
 * count of a file grows beyond the recorded baseline.
 *
 * Refresh captures with `ROS_URL=… ./gradlew captureHighlightOracle`.
 */
class RosHighlightOracleTest : UsefulTestCase() {
    private val sources = File("src/test/testData/lexer")
    private val oracleDir = File("src/test/testData/oracle")

    fun testAgainstRouterCaptures() {
        if (System.getProperty("ros.oracle.capture") == "true") capture()
        val report = StringBuilder()
        val regressions = mutableListOf<String>()
        for (file in sourceFiles()) {
            val capture = File(oracleDir, file.nameWithoutExtension + ".json").takeIf { it.isFile } ?: continue
            val json = JsonParser.parseString(capture.readText()).asJsonObject
            val text = file.readText()
            if (json.get("input").asString != text) {
                regressions += "${file.name}: capture is stale, run captureHighlightOracle"
                continue
            }
            val router = json.getAsJsonArray("highlight").map { it.asString }
            val mismatches = mismatches(text, router)
            val baseline = json.get("baselineMismatches")?.asInt ?: Int.MAX_VALUE
            report.append("${file.name}: ${mismatches.size} mismatching chars (baseline $baseline)\n")
            if (mismatches.size > baseline) {
                regressions += "${file.name}: ${mismatches.size} > baseline $baseline at offsets ${mismatches.take(20)}"
            }
        }
        File("build/reports").mkdirs()
        File("build/reports/highlight-oracle.txt").writeText(report.toString())
        assertEmpty(regressions)
    }

    private fun capture() {
        val client = RouterHighlightClient.fromEnvironment() ?: error("ROS_URL is not set")
        oracleDir.mkdirs()
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        for (file in sourceFiles()) {
            val text = file.readText()
            val highlight = client.highlight(text)
            val json = JsonObject().apply {
                addProperty("input", text)
                add("highlight", gson.toJsonTree(highlight))
                addProperty("baselineMismatches", mismatches(text, highlight).size)
            }
            File(oracleDir, file.nameWithoutExtension + ".json").writeText(gson.toJson(json) + "\n")
        }
    }

    private fun sourceFiles() = sources.listFiles { f -> f.extension == "rsc" }.orEmpty().sortedBy { it.name }

    private fun mismatches(text: String, router: List<String>): List<Int> {
        val ours = ourClasses(text)
        return ours.indices.filter { i ->
            val theirs = router.getOrNull(i)?.let(::routerClass) ?: return@filter false
            val mine = ours[i]
            mine != Coarse.IGNORE && theirs != Coarse.IGNORE && mine != theirs
        }
    }

    private fun ourClasses(text: String): Array<Coarse> {
        val result = Array(text.length) { Coarse.IGNORE }
        val lexer = RosLexerAdapter()
        lexer.start(text)
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!
            for (i in lexer.tokenStart until lexer.tokenEnd) {
                result[i] = ourClass(type, i == lexer.tokenStart)
            }
            lexer.advance()
        }
        return result
    }

    private fun ourClass(type: IElementType, first: Boolean): Coarse = when {
        type == RosTypes.COMMENT -> Coarse.COMMENT
        type == RosTypes.VARIABLE -> if (first) Coarse.PUNCT else Coarse.VARIABLE
        type == RosTypes.STRING_START || type == RosTypes.STRING_END -> Coarse.PUNCT
        type == RosTypes.STRING_ESCAPE -> Coarse.ESCAPE
        type == RosTypes.STRING_TEXT -> Coarse.TEXT
        type in RosTokenSets.SCRIPT_COMMANDS || type == RosTypes.PATH_WORD || type == RosTypes.SLASH -> Coarse.PATH
        type == RosTypes.ARG_NAME || type == RosTypes.CONTROL_ARG -> Coarse.NAME
        type == RosTypes.LBRACE || type == RosTypes.RBRACE || type == RosTypes.LBRACKET ||
            type == RosTypes.RBRACKET || type == RosTypes.LPAREN || type == RosTypes.RPAREN ||
            type == RosTypes.SEMICOLON || type == RosTypes.EQ || type == RosTypes.DOLLAR -> Coarse.PUNCT
        type == RosTypes.WORD || type == RosTypes.NUMBER || type == RosTypes.LITERAL || type == RosTypes.BOOLEAN -> Coarse.TEXT
        else -> Coarse.IGNORE
    }

    private fun routerClass(name: String): Coarse = when {
        name == "comment" -> Coarse.COMMENT
        name == "escaped" -> Coarse.ESCAPE
        name == "syntax-meta" -> Coarse.PUNCT
        name.startsWith("variable") -> Coarse.VARIABLE
        name == "dir" || name == "cmd" || name.startsWith("obj") -> Coarse.PATH
        name == "arg" -> Coarse.NAME
        name == "none" -> Coarse.TEXT
        else -> Coarse.IGNORE
    }

    private enum class Coarse { IGNORE, COMMENT, ESCAPE, PUNCT, VARIABLE, PATH, NAME, TEXT }
}
