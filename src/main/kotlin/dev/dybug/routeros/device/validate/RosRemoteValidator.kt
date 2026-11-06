package dev.dybug.routeros.device.validate

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import dev.dybug.routeros.device.RouterRestClient
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosTypes
import java.util.UUID

/** Problems reported by the router's own parser, with 1-based line and column. */
data class RouterProblem(val line: Int, val column: Int, val message: String)

/**
 * Validation with the router's parser: scripts through `/console/inspect request=highlight`
 * (first error position), export-style files through `/import … verbose=yes dry-run` (every error,
 * nothing applied).
 */
object RosRemoteValidator {
    private val LOCATION = Regex("""^(.*?)\s*\(line (\d+) column (\d+)\)\s*$""")

    /** An export-style file starts with a bare menu path such as `/ip address`. */
    fun isExportStyle(file: RosFile): Boolean {
        val first = file.children.firstOrNull {
            it !is PsiComment && it !is PsiWhiteSpace && it.node.elementType != RosTypes.NEWLINE && it.node.elementType != RosTypes.SEMICOLON
        }
        return first is RosCommand && RosPsiUtil.isMenuContextLine(first)
    }

    fun validateScript(client: RouterRestClient, text: String): List<RouterProblem> {
        val classes = client.highlight(text)
        val offset = classes.indexOfFirst { it == "error" }
        if (offset < 0) return emptyList()
        val (line, column) = lineColumn(text, offset)
        return listOf(RouterProblem(line, column, "syntax error"))
    }

    fun validateExport(client: RouterRestClient, text: String): List<RouterProblem> {
        val name = "ide-validate-" + UUID.randomUUID().toString().take(8) + ".rsc"
        client.putFile(name, text)
        try {
            return parseProblems(client.execute("/import file-name=$name verbose=yes dry-run"))
        } finally {
            runCatching { client.deleteFile(name) }
        }
    }

    /** Extracts `message (line N column M)` entries from router output. */
    fun parseProblems(output: String): List<RouterProblem> = output.lineSequence()
        .mapNotNull { LOCATION.matchEntire(it.trim()) }
        .mapNotNull { match ->
            val line = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val column = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            RouterProblem(line, column, match.groupValues[1].ifBlank { "error" })
        }
        .toList()

    fun lineColumn(text: String, offset: Int): Pair<Int, Int> {
        var line = 1
        var lineStart = 0
        for (i in 0 until minOf(offset, text.length)) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        return line to (offset - lineStart + 1)
    }
}
