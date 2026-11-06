package dev.dybug.routeros.device.sync

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.device.RosDeviceSettings
import dev.dybug.routeros.device.RouterConnections
import dev.dybug.routeros.device.RouterException
import dev.dybug.routeros.schema.RosProjectSchemaService
import dev.dybug.routeros.schema.RosSchemaService

/** Reads the router's command tree and stores it as the project's schema overlay. */
class RosSchemaSyncAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        sync(project)
    }

    companion object {
        fun sync(project: Project) {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, RosBundle.message("sync.progress"), true) {
                override fun run(indicator: ProgressIndicator) {
                    val group = NotificationGroupManager.getInstance().getNotificationGroup("RouterOS")
                    try {
                        val (profile, client) = RouterConnections.client(project, null)
                        val version = client.version()
                        RosDeviceSettings.getInstance(project).rememberVersion(profile.name, version)
                        val scriptCommands = RosSchemaService.getInstance().schema?.builtins?.scriptCommands?.keys.orEmpty()
                        val walker = RosDeviceSchemaWalker(client, scriptCommands)
                        val nodes = walker.walk(indicator)
                        RosProjectSchemaService.getInstance(project).store(version.substringBefore(' '), nodes)
                        EditorNotifications.getInstance(project).updateAllNotifications()
                        group.createNotification(
                            RosBundle.message("sync.done", nodes.size(), version, walker.requestCount),
                            NotificationType.INFORMATION,
                        ).notify(project)
                    } catch (e: RouterException) {
                        group.createNotification(RosBundle.message("sync.failed", e.message.orEmpty()), NotificationType.ERROR).notify(project)
                    }
                }
            })
        }
    }
}
