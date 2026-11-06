package dev.dybug.routeros.device.sync

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.RosFileType
import dev.dybug.routeros.device.RosDeviceSettings
import dev.dybug.routeros.schema.RosProjectSchemaService
import dev.dybug.routeros.schema.RosSchemaService
import java.util.function.Function
import javax.swing.JComponent

/** "Bundled schema is 7.26, router is 7.25 — Sync now / Ignore" on RouterOS files. */
class RosSchemaVersionNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.fileType != RosFileType) return null
        val routerVersion = RosDeviceSettings.getInstance(project).profile(null)?.lastSeenVersion?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
            ?: return null
        val schemaVersion = RosProjectSchemaService.getInstance(project).syncedVersion
            ?: RosSchemaService.getInstance().schema?.version ?: return null
        if (minor(routerVersion) == minor(schemaVersion)) return null
        val properties = PropertiesComponent.getInstance(project)
        if (properties.getValue(IGNORED_KEY) == routerVersion) return null
        return Function { editor ->
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
                text = RosBundle.message("sync.banner", schemaVersion, routerVersion)
                createActionLabel(RosBundle.message("sync.banner.sync")) { RosSchemaSyncAction.sync(project) }
                createActionLabel(RosBundle.message("sync.banner.ignore")) {
                    properties.setValue(IGNORED_KEY, routerVersion)
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
        }
    }

    private fun minor(version: String) = version.split('.').take(2).joinToString(".")

    private companion object {
        const val IGNORED_KEY = "routeros.schema.banner.ignored"
    }
}
