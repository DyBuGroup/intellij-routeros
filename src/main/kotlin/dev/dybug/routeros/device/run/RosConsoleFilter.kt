package dev.dybug.routeros.device.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Turns `… (line 12 column 5)` from router output into links to the script. */
class RosConsoleFilter(private val project: Project, private val file: VirtualFile) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = LOCATION.find(line) ?: return null
        val lineNumber = match.groupValues[1].toIntOrNull() ?: return null
        val column = match.groupValues[2].toIntOrNull() ?: return null
        val start = entireLength - line.length + match.range.first
        val end = entireLength - line.length + match.range.last + 1
        return Filter.Result(start, end, OpenFileHyperlinkInfo(project, file, maxOf(0, lineNumber - 1), maxOf(0, column - 1)))
    }

    private companion object {
        val LOCATION = Regex("""line (\d+) column (\d+)""")
    }
}
