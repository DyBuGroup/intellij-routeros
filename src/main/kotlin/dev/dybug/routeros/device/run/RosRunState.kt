package dev.dybug.routeros.device.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.device.RouterBackgroundRun
import dev.dybug.routeros.device.RouterConnections
import dev.dybug.routeros.device.RouterException
import dev.dybug.routeros.device.RouterRestClient
import dev.dybug.routeros.device.log.RouterLogTailer
import dev.dybug.routeros.device.validate.RosRemoteValidator
import dev.dybug.routeros.psi.RosFile
import java.util.UUID

/** Runs a script on the router through the REST API and streams the result into a console. */
class RosRunState(private val configuration: RosRunConfiguration, private val environment: ExecutionEnvironment) : RunProfileState {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val file = LocalFileSystem.getInstance().findFileByPath(configuration.scriptPath)
            ?: throw ExecutionException(RosBundle.message("run.error.file.missing", configuration.scriptPath))
        // run the saved text: the user sees exactly what was sent
        ApplicationManager.getApplication().invokeAndWait { FileDocumentManager.getInstance().saveAllDocuments() }
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.addMessageFilter(RosConsoleFilter(project, file))
        val run = Run(file)
        val handler = RosRunProcessHandler { run.stop() }
        run.handler = handler
        console.attachToProcess(handler)
        handler.startNotify()
        ApplicationManager.getApplication().executeOnPooledThread { run.execute() }
        return DefaultExecutionResult(console, handler)
    }

    private inner class Run(private val file: VirtualFile) {
        lateinit var handler: RosRunProcessHandler

        @Volatile
        private var background: RouterBackgroundRun? = null

        @Volatile
        private var importedFile: String? = null

        fun execute() {
            var tailer: RouterLogTailer? = null
            var exitCode = 0
            try {
                val (profile, client) = RouterConnections.client(environment.project, configuration.profile)
                val text = String(file.contentsToByteArray(), file.charset)
                val exportStyle = ApplicationManager.getApplication().runReadAction(
                    com.intellij.openapi.util.Computable {
                        (PsiManager.getInstance(environment.project).findFile(file) as? RosFile)?.let(RosRemoteValidator::isExportStyle) ?: false
                    },
                )
                val mode = when (configuration.mode) {
                    RunMode.AUTO -> if (exportStyle) RunMode.IMPORT else RunMode.EXECUTE
                    else -> configuration.mode
                }
                handler.print(RosBundle.message("run.console.start", file.name, profile.name, profile.url, mode.name.lowercase()), ProcessOutputType.SYSTEM)

                if (configuration.validateBeforeRun) {
                    val problems = if (mode == RunMode.IMPORT) {
                        RosRemoteValidator.validateExport(client, text)
                    } else {
                        RosRemoteValidator.validateScript(client, text)
                    }
                    if (problems.isNotEmpty()) {
                        handler.print(RosBundle.message("run.console.validation.failed"), ProcessOutputType.STDERR)
                        problems.forEach { handler.print("${it.message} (line ${it.line} column ${it.column})", ProcessOutputType.STDERR) }
                        handler.finish(2)
                        return
                    }
                }
                if (handler.cancelled) return

                if (handler.cancelled) return
                if (configuration.tailLog) {
                    tailer = RouterLogTailer(
                        client,
                        onEntry = { time, topics, message -> handler.print("[$time] $topics: $message", ProcessOutputType.SYSTEM) },
                        onError = { handler.print(RosBundle.message("run.console.log.failed", it), ProcessOutputType.STDERR) },
                    ).also { it.start() }
                }

                val prefix = if (mode == RunMode.UPLOAD_AND_RUN) {
                    "ide-" + file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_").take(32)
                } else {
                    "ide-run"
                }
                if (handler.cancelled) return
                val run = RouterBackgroundRun(client, prefix).also { background = it }
                val output = try {
                    run.start(if (mode == RunMode.IMPORT) importScript(client, text) else text)
                    // a Stop during the upload must still remove the job that was just started
                    if (handler.cancelled) run.stop()
                    run.await(profile.timeoutSeconds * 1000L) { !handler.cancelled }
                } finally {
                    run.cleanUp(keepScript = mode == RunMode.UPLOAD_AND_RUN && configuration.keepUploadedScript)
                    importedFile?.let { name -> runCatching { client.deleteFile(name) } }
                }
                tailer?.flush()
                output.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                    val error = RosRemoteValidator.parseProblems(line).isNotEmpty()
                    if (error) exitCode = 1
                    handler.print(line, if (error) ProcessOutputType.STDERR else ProcessOutputType.STDOUT)
                }
            } catch (e: RouterException) {
                handler.print(e.message.orEmpty(), ProcessOutputType.STDERR)
                exitCode = 1
            } catch (e: Exception) {
                handler.print(e.message ?: e.javaClass.simpleName, ProcessOutputType.STDERR)
                exitCode = 1
            } finally {
                tailer?.stop()
            }
            if (handler.cancelled) return
            handler.print(RosBundle.message("run.console.done", exitCode), ProcessOutputType.SYSTEM)
            handler.finish(exitCode)
        }

        /** Uploads the export as a file; the job then imports it. */
        private fun importScript(client: RouterRestClient, text: String): String {
            val name = "ide-import-" + UUID.randomUUID().toString().take(8) + ".rsc"
            client.putFile(name, text)
            importedFile = name
            return "/import file-name=$name verbose=yes"
        }

        /** Stop removes the background job. */
        fun stop() {
            val run = background ?: return
            ApplicationManager.getApplication().executeOnPooledThread { run.stop() }
        }
    }
}
