package dev.dybug.routeros.device

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.RosFileType
import dev.dybug.routeros.device.validate.RosRemoteValidator
import dev.dybug.routeros.device.validate.RouterProblem
import dev.dybug.routeros.psi.RosFile

private fun notify(project: Project, message: String, type: NotificationType) {
    NotificationGroupManager.getInstance().getNotificationGroup("RouterOS").createNotification(message, type).notify(project)
}

private fun background(project: Project, title: String, work: (ProgressIndicator) -> Unit) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
        override fun run(indicator: ProgressIndicator) {
            try {
                work(indicator)
            } catch (e: RouterException) {
                notify(project, e.message.orEmpty(), NotificationType.ERROR)
            }
        }
    })
}

abstract class RosFileAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType == RosFileType
    }
}

/** Checks the current file with the router's parser and marks the reported lines. */
class RosValidateOnRouterAction : RosFileAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? RosFile ?: return
        val text = editor?.document?.text ?: psiFile.text
        val export = RosRemoteValidator.isExportStyle(psiFile)
        background(project, RosBundle.message("validate.progress")) {
            val (_, client) = RouterConnections.client(project, null)
            val problems = if (export) RosRemoteValidator.validateExport(client, text) else RosRemoteValidator.validateScript(client, text)
            ApplicationManager.getApplication().invokeLater {
                if (problems.isEmpty()) {
                    notify(project, RosBundle.message("validate.ok"), NotificationType.INFORMATION)
                } else {
                    if (editor != null && !editor.isDisposed) mark(editor, problems)
                    val first = problems.first()
                    notify(project, RosBundle.message("validate.problems", problems.size, first.line, first.message), NotificationType.WARNING)
                    psiFile.virtualFile?.let { OpenFileDescriptor(project, it, first.line - 1, first.column - 1).navigate(true) }
                }
            }
        }
    }

    private fun mark(editor: com.intellij.openapi.editor.Editor, problems: List<RouterProblem>) {
        val document = editor.document
        val markup = editor.markupModel
        editor.getUserData(MARKERS)?.forEach { markup.removeHighlighter(it) }
        val markers = mutableListOf<com.intellij.openapi.editor.markup.RangeHighlighter>()
        editor.putUserData(MARKERS, markers)
        for (problem in problems) {
            val line = problem.line - 1
            if (line !in 0 until document.lineCount) continue
            markup.addRangeHighlighter(
                CodeInsightColors.ERRORS_ATTRIBUTES,
                document.getLineStartOffset(line), document.getLineEndOffset(line),
                HighlighterLayer.ERROR, HighlighterTargetArea.EXACT_RANGE,
            ).also {
                it.errorStripeTooltip = problem.message
                markers += it
            }
        }
    }

    private companion object {
        val MARKERS = com.intellij.openapi.util.Key.create<List<com.intellij.openapi.editor.markup.RangeHighlighter>>("RouterOS.validation.markers")
    }
}

/** Opens `/export` of the default router in a new editor. */
class RosFetchExportAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        background(project, RosBundle.message("export.progress")) {
            val (profile, client) = RouterConnections.client(project, null)
            val export = client.execute("/export")
            ApplicationManager.getApplication().invokeLater {
                val file = LightVirtualFile("${profile.name}-export.rsc", RosFileType, export)
                FileEditorManager.getInstance(project).openFile(file, true)
            }
        }
    }
}

/** Compares the current file with the router's `/export`. */
class RosCompareWithRouterAction : RosFileAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file: VirtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        background(project, RosBundle.message("export.progress")) {
            val (profile, client) = RouterConnections.client(project, null)
            val export = client.execute("/export")
            ApplicationManager.getApplication().invokeLater {
                val factory = DiffContentFactory.getInstance()
                val request = SimpleDiffRequest(
                    RosBundle.message("compare.title", file.name, profile.name),
                    factory.create(project, file),
                    factory.create(project, export, RosFileType),
                    file.name,
                    RosBundle.message("compare.router", profile.name),
                )
                DiffManager.getInstance().showDiff(project, request)
            }
        }
    }
}
