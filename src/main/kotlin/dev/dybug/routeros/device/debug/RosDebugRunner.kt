package dev.dybug.routeros.device.debug

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.execution.ExecutionException
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.device.RouterConnections
import dev.dybug.routeros.device.RouterRestClient
import dev.dybug.routeros.device.run.RosConsoleFilter
import dev.dybug.routeros.device.run.RosRunConfiguration
import dev.dybug.routeros.device.run.RosRunProcessHandler

/** Debug executor for RouterOS run configurations; enabled with the "RouterOS: experimental debugger" advanced setting. */
class RosDebugRunner : GenericProgramRunner<com.intellij.execution.configurations.RunnerSettings>() {
    override fun getRunnerId(): String = "RouterOSDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is RosRunConfiguration && AdvancedSettings.getBoolean(SETTING)

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val configuration = environment.runProfile as RosRunConfiguration
        val project = environment.project
        val file = LocalFileSystem.getInstance().findFileByPath(configuration.scriptPath)
            ?: throw ExecutionException(RosBundle.message("run.error.file.missing", configuration.scriptPath))
        FileDocumentManager.getInstance().saveAllDocuments()
        val client: RouterRestClient = ProgressManager.getInstance().runProcessWithProgressSynchronously<RouterRestClient, Exception>(
            { RouterConnections.client(project, configuration.profile).second },
            RosBundle.message("debug.connecting"), true, project,
        )
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.addMessageFilter(RosConsoleFilter(project, file))
        lateinit var process: RosDebugProcess
        val handler = RosRunProcessHandler { process.stop() }
        console.attachToProcess(handler)
        val session = XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess = RosDebugProcess(session, client, file, handler, console).also { process = it }
        })
        ApplicationManager.getApplication().invokeLater { handler.startNotify() }
        return session.runContentDescriptor
    }

    companion object {
        const val SETTING = "routeros.debugger.enabled"
    }
}
