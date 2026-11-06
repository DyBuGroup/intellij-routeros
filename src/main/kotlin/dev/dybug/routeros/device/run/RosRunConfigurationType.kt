package dev.dybug.routeros.device.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.RosIcons

class RosRunConfigurationType : ConfigurationTypeBase(
    ID,
    RosBundle.message("run.type.name"),
    RosBundle.message("run.type.description"),
    NotNullLazyValue.createValue { RosIcons.FILE },
) {
    init {
        addFactory(Factory(this))
    }

    class Factory(type: RosRunConfigurationType) : ConfigurationFactory(type) {
        override fun getId(): String = ID

        override fun createTemplateConfiguration(project: Project): RunConfiguration =
            RosRunConfiguration(project, this, RosBundle.message("run.type.name"))

        override fun getOptionsClass(): Class<out BaseState> = RosRunConfigurationOptions::class.java
    }

    companion object {
        const val ID = "RouterOSScript"
    }
}
