package dev.dybug.routeros.device.debug

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.RosFileType
import dev.dybug.routeros.device.RouterRestClient
import dev.dybug.routeros.device.run.RosRunProcessHandler
import dev.dybug.routeros.psi.RosFile
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Experimental: runs an instrumented copy of the script as `/system script ide-dbg-<token>` and
 * polls the user's global environment for pauses. Resume/step set globals over REST.
 */
class RosDebugProcess(
    session: XDebugSession,
    private val client: RouterRestClient,
    private val file: VirtualFile,
    private val handler: RosRunProcessHandler,
    private val console: ExecutionConsole,
) : XDebugProcess(session) {
    private val token = UUID.randomUUID().toString().replace("-", "").take(8)
    private val instrumenter = RosInstrumenter(token)
    private val controller = RouterDebugController(client, instrumenter)
    @Volatile
    private var background: dev.dybug.routeros.device.RouterBackgroundRun? = null
    private val breakpoints = mutableSetOf<XLineBreakpoint<XBreakpointProperties<*>>>()
    private var poller: ScheduledFuture<*>? = null

    @Volatile
    private var lastPause: RouterDebugController.Pause? = null

    @Volatile
    private var finished = false

    private val breakpointHandler = object : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(RosLineBreakpointType::class.java) {
        override fun registerBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>) {
            if (breakpoint.fileUrl != file.url) return
            synchronized(breakpoints) { breakpoints += breakpoint }
            pushBreakpoints()
        }

        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) {
            synchronized(breakpoints) { breakpoints -= breakpoint }
            pushBreakpoints()
        }
    }

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> = arrayOf(breakpointHandler)

    override fun getEditorsProvider(): XDebuggerEditorsProvider = object : XDebuggerEditorsProviderBase() {
        override fun getFileType() = RosFileType

        override fun createExpressionCodeFragment(
            project: com.intellij.openapi.project.Project,
            text: String,
            context: com.intellij.psi.PsiElement?,
            isPhysical: Boolean,
        ): com.intellij.psi.PsiFile = dev.dybug.routeros.psi.RosElementFactory.file(project, text)
    }

    override fun createConsole(): ExecutionConsole = console

    override fun doGetProcessHandler() = handler

    override fun sessionInitialized() {
        handler.print(RosBundle.message("debug.experimental.warning"), ProcessOutputType.SYSTEM)
        ApplicationManager.getApplication().executeOnPooledThread(::run)
    }

    private fun run() {
        try {
            val instrumented = ReadAction.compute<RosInstrumenter.Result?, RuntimeException> {
                (PsiManager.getInstance(session.project).findFile(file) as? RosFile)?.let(instrumenter::instrument)
            } ?: return
            controller.prepare(breakpointLines())
            // a background job keeps the REST service free for polling and resume calls
            val run = dev.dybug.routeros.device.RouterBackgroundRun(client, "ide-dbg").also { background = it }
            run.start(instrumented.text)
            poller = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(::poll, 200, 250, TimeUnit.MILLISECONDS)
            val output = run.await(timeoutMillis = 24L * 3600 * 1000) { !finished }
            output.lineSequence().filter { it.isNotEmpty() }.forEach { handler.print(it) }
            handler.print(RosBundle.message("run.console.done", 0), ProcessOutputType.SYSTEM)
        } catch (e: Exception) {
            handler.print(e.message ?: e.javaClass.simpleName, ProcessOutputType.STDERR)
        } finally {
            finish()
        }
    }

    private fun poll() {
        if (finished) return
        val pause = runCatching { controller.pause() }.getOrNull()
        if (pause == null) {
            lastPause = null
            return
        }
        if (pause == lastPause) return
        lastPause = pause
        val position = XDebuggerUtil.getInstance().createPosition(file, pause.line - 1) ?: return
        session.positionReached(SuspendContext(position, pause.variables))
    }

    private fun breakpointLines(): Set<Int> = synchronized(breakpoints) { breakpoints.filter { it.isEnabled }.map { it.line }.toSet() }

    private fun pushBreakpoints() {
        if (finished) return
        ApplicationManager.getApplication().executeOnPooledThread { runCatching { controller.setBreakpoints(breakpointLines()) } }
    }

    private fun send(step: Boolean) {
        lastPause = null
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching { controller.resume(step) }.onFailure { handler.print(it.message.orEmpty(), ProcessOutputType.STDERR) }
        }
    }

    override fun resume(context: XSuspendContext?) = send(step = false)

    override fun startStepOver(context: XSuspendContext?) = send(step = true)

    override fun startStepInto(context: XSuspendContext?) = send(step = true)

    override fun startStepOut(context: XSuspendContext?) = send(step = false)

    override fun startPausing() {
        // a running script can only be paused at a pause point: switch to step mode
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching { client.execute(":global ${instrumenter.stepVar}; :set ${instrumenter.stepVar} true") }
        }
    }

    override fun stop() {
        ApplicationManager.getApplication().executeOnPooledThread {
            background?.stop()
            finish()
        }
    }

    @Synchronized
    private fun finish() {
        if (finished) return
        finished = true
        poller?.cancel(false)
        controller.cleanUp()
        background?.cleanUp()
        handler.finish(0)
    }

    private class SuspendContext(position: XSourcePosition, variables: JsonObject) : XSuspendContext() {
        private val stack = Stack(Frame(position, variables))

        override fun getActiveExecutionStack(): XExecutionStack = stack

        override fun getExecutionStacks(): Array<XExecutionStack> = arrayOf(stack)
    }

    private class Stack(private val frame: Frame) : XExecutionStack(RosBundle.message("debug.stack")) {
        override fun getTopFrame(): XStackFrame = frame

        override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
            container.addStackFrames(if (firstFrameIndex == 0) listOf(frame) else emptyList(), true)
        }
    }

    private class Frame(private val position: XSourcePosition, private val variables: JsonObject) : XStackFrame() {
        override fun getSourcePosition(): XSourcePosition = position

        override fun computeChildren(node: XCompositeNode) = node.addChildren(children(variables), true)
    }

    private class Value(name: String, private val json: JsonElement) : XNamedValue(name) {
        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            val (type, text) = when {
                json.isJsonNull -> "nothing" to ""
                json is JsonArray -> "array" to "[${json.size()}]"
                json is JsonObject -> "array" to "{${json.size()}}"
                json.asJsonPrimitive.isBoolean -> "bool" to json.asString
                json.asJsonPrimitive.isNumber -> "num" to json.asString
                else -> "str" to "\"" + json.asString + "\""
            }
            node.setPresentation(AllIcons.Nodes.Variable, type, text, json is JsonArray || json is JsonObject)
        }

        override fun computeChildren(node: XCompositeNode) {
            when (json) {
                is JsonObject -> node.addChildren(children(json), true)
                is JsonArray -> node.addChildren(XValueChildrenList().apply { json.forEachIndexed { i, e -> add(Value(i.toString(), e)) } }, true)
                else -> node.addChildren(XValueChildrenList.EMPTY, true)
            }
        }
    }

    private companion object {
        fun children(json: JsonObject) = XValueChildrenList().apply { json.entrySet().forEach { (k, v) -> add(Value(k, v)) } }
    }
}
