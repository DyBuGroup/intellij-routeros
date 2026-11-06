package dev.dybug.routeros.device.debug

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import dev.dybug.routeros.device.RouterRestClient

/** The router side of a debug session: globals used as a rendezvous with the instrumented script. */
class RouterDebugController(private val client: RouterRestClient, val instrumenter: RosInstrumenter) {
    /** [sequence] grows with every pause, so a repeated pause at the same line is still a new pause. */
    data class Pause(val sequence: Long, val line: Int, val variables: JsonObject)

    private val lock = Any()

    fun prepare(breakpointLines: Collection<Int>) {
        client.execute(
            ":global ${instrumenter.breakpointsVar} \"${breakpointList(breakpointLines)}\"; " +
                ":global ${instrumenter.stepVar} false; :global ${instrumenter.pauseVar} false; :global ${instrumenter.stateVar} \"\"; " +
                ":global ${instrumenter.sequenceVar} 0",
        )
    }

    fun setBreakpoints(breakpointLines: Collection<Int>) = synchronized(lock) {
        client.execute(":global ${instrumenter.breakpointsVar}; :set ${instrumenter.breakpointsVar} \"${breakpointList(breakpointLines)}\"")
    }

    /** The current pause, or null while the script runs. */
    fun pause(): Pause? = synchronized(lock) {
        val environment = client.environment()
        if (environment[instrumenter.pauseVar] != "true") return null
        val state = environment[instrumenter.stateVar].orEmpty()
        return try {
            val json = JsonParser.parseString(state) as? JsonObject ?: return UNREADABLE
            // :serialize writes an empty array as [] rather than {}
            val variables = json.get("vars") as? JsonObject ?: JsonObject()
            Pause(json.get("seq")?.asLong ?: 0L, json.get("line").asInt, variables)
        } catch (e: RuntimeException) {
            if (e is JsonSyntaxException || e is IllegalStateException || e is NullPointerException ||
                e is UnsupportedOperationException || e is NumberFormatException
            ) {
                UNREADABLE
            } else {
                throw e
            }
        }
    }

    fun resume(step: Boolean) = synchronized(lock) {
        client.execute(
            ":global ${instrumenter.stepVar}; :global ${instrumenter.pauseVar}; " +
                ":set ${instrumenter.stepVar} $step; :set ${instrumenter.pauseVar} false",
        )
    }

    /** Removes the session's globals; best effort. */
    fun cleanUp() = synchronized(lock) {
        runCatching {
            client.execute("/system script environment remove [find where name~\"^RosDbg${instrumenter.token}\"]")
        }
    }

    companion object {
        /** The script is paused but its state could not be read (e.g. :serialize failed). */
        val UNREADABLE = Pause(-1, 0, JsonObject())
    }

    /** 0-based editor lines → `,1,5,` (1-based) as checked by the pause points. */
    private fun breakpointList(lines: Collection<Int>): String = lines.sorted().joinToString(",", prefix = ",", postfix = ",") { (it + 1).toString() }
}
