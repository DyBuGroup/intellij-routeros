package dev.dybug.routeros.device.log

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.AppExecutorUtil
import dev.dybug.routeros.device.RouterException
import dev.dybug.routeros.device.RouterRestClient
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Polls `/rest/log` and reports entries newer than the last one seen. RouterOS log ids are
 * hexadecimal (`*1A`); entries are compared numerically.
 */
class RouterLogTailer(
    private val client: RouterRestClient,
    private val intervalMillis: Long = 1000,
    private val onEntry: (time: String, topics: String, message: String) -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile
    private var lastId = -1L
    private var future: ScheduledFuture<*>? = null
    private var failures = 0
    private var initialised = true

    /** Starts after the newest current entry, so only new messages are shown. */
    fun start(showExisting: Int = 0) {
        val entries = runCatching { client.log() }.getOrNull()
        if (entries == null) {
            // the first successful poll only sets the cursor, so old entries are not shown as new
            initialised = false
        } else {
            entries.takeLast(showExisting).forEach(::emit)
            lastId = entries.maxOfOrNull { id(it) } ?: -1L
        }
        future = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(::poll, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        future?.cancel(false)
        future = null
    }

    /** One final poll, e.g. right after a run finished. */
    fun flush() = poll()

    @Synchronized
    private fun poll() {
        try {
            val entries = client.log()
            if (!initialised) {
                lastId = entries.maxOfOrNull { id(it) } ?: -1L
                initialised = true
                return
            }
            // the router restarted or the log was cleared: ids start over
            val newest = entries.maxOfOrNull { id(it) } ?: lastId
            if (newest < lastId) lastId = -1L
            val fresh = entries.filter { id(it) > lastId }
            fresh.forEach(::emit)
            if (newest > lastId) lastId = newest
            failures = 0
        } catch (e: RouterException) {
            if (++failures == 3) onError(e.message.orEmpty())
        } catch (e: Exception) {
            LOG.warn(e)
        }
    }

    private fun emit(entry: Map<String, String>) {
        onEntry(entry["time"].orEmpty(), entry["topics"].orEmpty(), entry["message"].orEmpty())
    }

    private fun id(entry: Map<String, String>): Long = entry[".id"]?.removePrefix("*")?.toLongOrNull(16) ?: -1L

    private companion object {
        val LOG = logger<RouterLogTailer>()
    }
}
