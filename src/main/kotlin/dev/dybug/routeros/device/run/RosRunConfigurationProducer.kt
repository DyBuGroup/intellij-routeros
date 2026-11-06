package dev.dybug.routeros.device.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import dev.dybug.routeros.RosFileType

class RosRunConfigurationProducer : LazyRunConfigurationProducer<RosRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(RosRunConfigurationType::class.java).configurationFactories.single()

    override fun setupConfigurationFromContext(
        configuration: RosRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (file.fileType != RosFileType || !file.isInLocalFileSystem) return false
        configuration.scriptPath = file.path
        configuration.name = file.nameWithoutExtension
        return true
    }

    override fun isConfigurationFromContext(configuration: RosRunConfiguration, context: ConfigurationContext): Boolean {
        val file = context.location?.virtualFile ?: return false
        return configuration.scriptPath == file.path
    }
}
