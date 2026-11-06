package dev.dybug.routeros.device.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.device.RosDeviceSettings
import javax.swing.JComponent

class RosRunSettingsEditor(private val project: Project) : SettingsEditor<RosRunConfiguration>() {
    private val profileCombo = ComboBox<String>()
    private val fileField = TextFieldWithBrowseButton()
    private val modeCombo = ComboBox(RunMode.entries.toTypedArray())
    private val validateCheck = JBCheckBox(RosBundle.message("run.editor.validate"))
    private val tailCheck = JBCheckBox(RosBundle.message("run.editor.tail"))
    private val keepCheck = JBCheckBox(RosBundle.message("run.editor.keep"))

    init {
        fileField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("rsc").withTitle(RosBundle.message("run.editor.file")),
        )
    }

    override fun resetEditorFrom(configuration: RosRunConfiguration) {
        profileCombo.removeAllItems()
        profileCombo.addItem("")
        RosDeviceSettings.getInstance(project).profiles.forEach { profileCombo.addItem(it.name) }
        profileCombo.selectedItem = configuration.profile.orEmpty()
        fileField.text = configuration.scriptPath
        modeCombo.selectedItem = configuration.mode
        validateCheck.isSelected = configuration.validateBeforeRun
        tailCheck.isSelected = configuration.tailLog
        keepCheck.isSelected = configuration.keepUploadedScript
    }

    override fun applyEditorTo(configuration: RosRunConfiguration) {
        configuration.profile = (profileCombo.selectedItem as? String)?.ifEmpty { null }
        configuration.scriptPath = fileField.text.trim()
        configuration.mode = modeCombo.selectedItem as? RunMode ?: RunMode.AUTO
        configuration.validateBeforeRun = validateCheck.isSelected
        configuration.tailLog = tailCheck.isSelected
        configuration.keepUploadedScript = keepCheck.isSelected
    }

    override fun createEditor(): JComponent = panel {
        row(RosBundle.message("run.editor.file")) { cell(fileField).align(AlignX.FILL) }
        row(RosBundle.message("run.editor.profile")) { cell(profileCombo).comment(RosBundle.message("run.editor.profile.comment")) }
        row(RosBundle.message("run.editor.mode")) { cell(modeCombo).comment(RosBundle.message("run.editor.mode.comment")) }
        row { cell(validateCheck) }
        row { cell(tailCheck) }
        row { cell(keepCheck) }
    }
}
