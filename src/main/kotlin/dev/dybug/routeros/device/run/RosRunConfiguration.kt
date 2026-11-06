package dev.dybug.routeros.device.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.device.RosDeviceSettings

class RosRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<RosRunConfigurationOptions>(project, factory, name) {
    public override fun getOptions(): RosRunConfigurationOptions = super.getOptions() as RosRunConfigurationOptions

    var profile: String?
        get() = options.profile
        set(value) {
            options.profile = value
        }

    var scriptPath: String
        get() = options.scriptPath.orEmpty()
        set(value) {
            options.scriptPath = value
        }

    var mode: RunMode
        get() = options.mode
        set(value) {
            options.mode = value
        }

    var validateBeforeRun: Boolean
        get() = options.validateBeforeRun
        set(value) {
            options.validateBeforeRun = value
        }

    var tailLog: Boolean
        get() = options.tailLog
        set(value) {
            options.tailLog = value
        }

    var keepUploadedScript: Boolean
        get() = options.keepUploadedScript
        set(value) {
            options.keepUploadedScript = value
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = RosRunSettingsEditor(project)

    override fun checkConfiguration() {
        if (scriptPath.isBlank()) throw RuntimeConfigurationError(RosBundle.message("run.error.no.file"))
        if (LocalFileSystem.getInstance().findFileByPath(scriptPath) == null) {
            throw RuntimeConfigurationError(RosBundle.message("run.error.file.missing", scriptPath))
        }
        if (RosDeviceSettings.getInstance(project).profile(profile) == null) {
            throw RuntimeConfigurationError(RosBundle.message("device.no.profile"))
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = RosRunState(this, environment)
}
