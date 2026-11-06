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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import dev.dybug.routeros.RosBundle
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
        private var client: RouterRestClient? = null

        @Volatile
        private var uploadedScript: String? = null

        fun execute() {
            var tailer: RouterLogTailer? = null
            var exitCode = 0
            try {
                val (profile, client) = RouterConnections.client(environment.project, configuration.profile)
                this.client = client
                val text = String(file.contentsToByteArray(), file.charset)
                val exportStyle = ReadAction.compute<Boolean, RuntimeException> {
                    (PsiManager.getInstance(environment.project).findFile(file) as? RosFile)?.let(RosRemoteValidator::isExportStyle) ?: false
                }
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

                if (configuration.tailLog) {
                    tailer = RouterLogTailer(
                        client,
                        onEntry = { time, topics, message -> handler.print("[$time] $topics: $message", ProcessOutputType.SYSTEM) },
                        onError = { handler.print(RosBundle.message("run.console.log.failed", it), ProcessOutputType.STDERR) },
                    ).also { it.start() }
                }

                val output = when (mode) {
                    RunMode.IMPORT -> runImport(client, text)
                    RunMode.UPLOAD_AND_RUN -> runUploaded(client, text)
                    else -> client.execute(text)
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
                cleanUp()
            }
            handler.print(RosBundle.message("run.console.done", exitCode), ProcessOutputType.SYSTEM)
            handler.finish(exitCode)
        }

        private fun runImport(client: RouterRestClient, text: String): String {
            val name = "ide-run-" + UUID.randomUUID().toString().take(8) + ".rsc"
            client.putFile(name, text)
            try {
                return client.execute("/import file-name=$name verbose=yes")
            } finally {
                runCatching { client.deleteFile(name) }
            }
        }

        private fun runUploaded(client: RouterRestClient, text: String): String {
            val name = "ide-" + file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
            runCatching { client.deleteScript(name) }
            client.putScript(name, text)
            uploadedScript = name
            return client.execute("/system script run $name")
        }

        /** Stop: remove the job of the uploaded script; an inline /execute cannot be interrupted safely. */
        fun stop() {
            val client = client ?: return
            ApplicationManager.getApplication().executeOnPooledThread {
                val script = uploadedScript
                if (script != null) {
                    runCatching { client.jobs().filter { it.second == script }.forEach { client.removeJob(it.first) } }
                }
                cleanUp()
            }
        }

        private fun cleanUp() {
            val script = uploadedScript ?: return
            if (configuration.keepUploadedScript) return
            uploadedScript = null
            runCatching { client?.deleteScript(script) }
        }
    }
}
