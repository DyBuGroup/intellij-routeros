package dev.dybug.routeros.device.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import java.io.OutputStream

/** Stands in for a process: the "process" is a sequence of REST calls running on a pooled thread. */
class RosRunProcessHandler(private val onStop: () -> Unit) : ProcessHandler() {
    @Volatile
    var cancelled = false
        private set

    override fun destroyProcessImpl() {
        cancelled = true
        onStop()
        notifyProcessTerminated(130)
    }

    override fun detachProcessImpl() {
        cancelled = true
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null

    fun print(text: String, type: Key<*> = ProcessOutputType.STDOUT) {
        if (!isProcessTerminated) notifyTextAvailable(if (text.endsWith("\n")) text else text + "\n", type)
    }

    fun finish(exitCode: Int) {
        if (!isProcessTerminated && !isProcessTerminating) notifyProcessTerminated(exitCode)
    }
}
