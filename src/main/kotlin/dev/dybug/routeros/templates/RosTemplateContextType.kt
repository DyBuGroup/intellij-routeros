package dev.dybug.routeros.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosFile

class RosTemplateContextType : TemplateContextType(RosBundle.message("color.settings.name")) {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean = templateActionContext.file is RosFile
}
