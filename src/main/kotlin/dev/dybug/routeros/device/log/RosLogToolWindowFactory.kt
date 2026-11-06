package dev.dybug.routeros.device.log

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.content.ContentFactory
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.device.RouterConnections
import java.awt.BorderLayout
import javax.swing.JPanel

/** Router log tail: polls `/rest/log` of the default connection while switched on. */
class RosLogToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = LogPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        Disposer.register(content, panel)
        toolWindow.contentManager.addContent(content)
    }

    private class LogPanel(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {
        private val console: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        private val filter = SearchTextField(false).apply { textEditor.emptyText.text = RosBundle.message("log.filter.hint") }

        private val lock = Any()
        private var tailer: RouterLogTailer? = null

        /** Bumped by every start/stop/dispose; a startup that finishes after a newer transition is discarded. */
        private var generation = 0
        private var disposed = false

        @Volatile
        private var filterText = ""

        init {
            Disposer.register(this, console)
            filter.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
                override fun textChanged(e: javax.swing.event.DocumentEvent) {
                    filterText = filter.text.trim()
                }
            })
            val top = JPanel(BorderLayout()).apply { add(filter, BorderLayout.CENTER) }
            val center = JPanel(BorderLayout()).apply {
                add(top, BorderLayout.NORTH)
                add(console.component, BorderLayout.CENTER)
            }
            setContent(center)
            val group = DefaultActionGroup(object : ToggleAction(RosBundle.message("log.follow"), null, AllIcons.Actions.Execute), DumbAware {
                override fun isSelected(e: AnActionEvent): Boolean = synchronized(lock) { tailer != null || starting }

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state) start() else stop()
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            })
            group.addAll(*console.createConsoleActions())
            val toolbar = ActionManager.getInstance().createActionToolbar("RouterOSLog", group, false)
            toolbar.targetComponent = this
            setToolbar(toolbar.component)
        }

        private var starting = false

        private fun start() {
            val myGeneration = synchronized(lock) {
                if (disposed || tailer != null || starting) return
                starting = true
                ++generation
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val (profile, client) = RouterConnections.client(project, null)
                    console.print(RosBundle.message("log.started", profile.name, profile.url) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                    val created = RouterLogTailer(
                        client,
                        onEntry = { time, topics, message ->
                            val line = "[$time] $topics: $message"
                            val needle = filterText
                            if (needle.isEmpty() || line.contains(needle, ignoreCase = true)) {
                                val type = if ("error" in topics || "critical" in topics) ConsoleViewContentType.ERROR_OUTPUT else ConsoleViewContentType.NORMAL_OUTPUT
                                console.print(line + "\n", type)
                            }
                        },
                        onError = { console.print(it + "\n", ConsoleViewContentType.ERROR_OUTPUT) },
                    )
                    created.start(showExisting = 20)
                    val keep = synchronized(lock) {
                        starting = false
                        if (disposed || generation != myGeneration) {
                            false
                        } else {
                            tailer = created
                            true
                        }
                    }
                    if (!keep) created.stop()
                } catch (e: Exception) {
                    synchronized(lock) { starting = false }
                    console.print((e.message ?: e.javaClass.simpleName) + "\n", ConsoleViewContentType.ERROR_OUTPUT)
                }
            }
        }

        private fun stop() {
            val stopped = synchronized(lock) {
                generation++
                starting = false
                tailer.also { tailer = null }
            }
            stopped?.stop()
            console.print(RosBundle.message("log.stopped") + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        }

        override fun dispose() {
            val stopped = synchronized(lock) {
                disposed = true
                generation++
                tailer.also { tailer = null }
            }
            stopped?.stop()
        }
    }
}
