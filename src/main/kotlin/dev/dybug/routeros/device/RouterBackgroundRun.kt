package dev.dybug.routeros.device

import java.util.UUID

/**
 * Runs a script as a background job with its output captured in a router file. Unlike a
 * blocking `/execute`, this keeps the REST service free for log polling and debugger calls.
 */
class RouterBackgroundRun(private val client: RouterRestClient, prefix: String = "ide-run") {
    private val token = UUID.randomUUID().toString().replace("-", "").take(8)
    val scriptName = "$prefix-$token"
    private val outputFile = "$scriptName-out.txt"

    init {
        require(scriptName.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "unsafe script name" }
    }

    fun start(source: String) {
        client.putScript(scriptName, source)
        client.executeInBackground(":execute script=\"/system script run $scriptName\" file=\"$outputFile\"")
    }

    /**
     * Waits until the job ends; [onTick] runs about every [pollMillis] and may return false to stop
     * waiting. Returns the captured output.
     */
    fun await(timeoutMillis: Long, pollMillis: Long = 500, onTick: () -> Boolean = { true }): String {
        val started = System.currentTimeMillis()
        var seenRunning = false
        while (true) {
            if (!onTick()) break
            val running = client.jobs().any { it.second == scriptName }
            seenRunning = seenRunning || running
            val elapsed = System.currentTimeMillis() - started
            if (!running && (seenRunning || elapsed > 3_000 || client.fileContents(outputFile) != null)) break
            if (elapsed > timeoutMillis) {
                stop()
                throw RouterException("The script did not finish within ${timeoutMillis / 1000} s and was stopped")
            }
            Thread.sleep(pollMillis)
        }
        // the output file appears (and is filled) shortly after the job ends; an empty result is
        // accepted only after it stayed empty for a while
        var last: String? = null
        repeat(15) {
            val contents = client.fileContents(outputFile)
            if (!contents.isNullOrEmpty()) return contents
            last = contents
            Thread.sleep(200)
        }
        return last.orEmpty()
    }

    fun stop() {
        runCatching { client.jobs().filter { it.second == scriptName }.forEach { client.removeJob(it.first) } }
    }

    /** Stops a still running job first, then removes the script (unless kept) and the output file. */
    fun cleanUp(keepScript: Boolean = false) {
        stop()
        if (!keepScript) runCatching { client.deleteScript(scriptName) }
        runCatching { client.deleteFile(outputFile) }
    }
}
