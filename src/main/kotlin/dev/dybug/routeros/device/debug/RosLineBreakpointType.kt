package dev.dybug.routeros.device.debug

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.RosFileType

class RosLineBreakpointType : XLineBreakpointType<XBreakpointProperties<*>>(ID, RosBundle.message("debug.breakpoint.type")) {
    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        if (file.fileType != RosFileType) return false
        val document: Document = FileDocumentManager.getInstance().getDocument(file) ?: return false
        if (line !in 0 until document.lineCount) return false
        val text = document.getText(com.intellij.openapi.util.TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))).trim()
        return text.isNotEmpty() && !text.startsWith("#") && text != "}"
    }

    override fun createBreakpointProperties(file: VirtualFile, line: Int): XBreakpointProperties<*>? = null

    companion object {
        const val ID = "routeros-line"
    }
}
