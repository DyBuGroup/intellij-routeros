package dev.dybug.routeros.templates

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.RosIcons

class RosCreateFileAction : CreateFileFromTemplateAction(), DumbAware {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle(RosBundle.message("action.new.file.title"))
            .addKind(RosBundle.message("action.new.file.script"), RosIcons.FILE, TEMPLATE_NAME)
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
        RosBundle.message("action.new.file.title")

    companion object {
        const val TEMPLATE_NAME = "RouterOS Script"
    }
}
