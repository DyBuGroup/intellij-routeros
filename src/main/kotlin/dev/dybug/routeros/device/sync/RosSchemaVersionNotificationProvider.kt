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
import dev.dybug.routeros.schema.RosSchemaLoader
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shown on RouterOS files when the schema in use does not match the connected router's version:
 * offers the bundled schema for that version (if pinned to another one) or a sync from the router.
 */
class RosSchemaVersionNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.fileType != RosFileType) return null
        val settings = RosDeviceSettings.getInstance(project)
        val routerVersion = settings.profile(null)?.lastSeenVersion?.substringBefore(' ')?.takeIf { it.isNotEmpty() } ?: return null
        val routerMinor = RosProjectSchemaService.minorOf(routerVersion) ?: return null
        val service = RosProjectSchemaService.getInstance(project)
        val schemaVersion = service.syncedVersion?.let(RosProjectSchemaService::minorOf) ?: service.effectiveVersion
        if (routerMinor == schemaVersion) return null
        val properties = PropertiesComponent.getInstance(project)
        if (properties.getValue(IGNORED_KEY) == routerVersion) return null
        // only useful when a pinned version differs; with "auto" the router's version is already in use
        val bundledMatch = routerMinor in RosSchemaLoader.bundledVersions && service.effectiveVersion != routerMinor
        return Function { editor ->
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
                text = RosBundle.message("sync.banner", schemaVersion, routerVersion)
                if (bundledMatch) {
                    createActionLabel(RosBundle.message("sync.banner.use.bundled", routerMinor)) {
                        settings.state.schemaVersion = RosDeviceSettings.AUTO_SCHEMA
                        service.selectionChanged()
                        EditorNotifications.getInstance(project).updateAllNotifications()
                    }
                }
                createActionLabel(RosBundle.message("sync.banner.sync")) { RosSchemaSyncAction.sync(project) }
                createActionLabel(RosBundle.message("sync.banner.ignore")) {
                    properties.setValue(IGNORED_KEY, routerVersion)
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
        }
    }

    private companion object {
        const val IGNORED_KEY = "routeros.schema.banner.ignored"
    }
}
