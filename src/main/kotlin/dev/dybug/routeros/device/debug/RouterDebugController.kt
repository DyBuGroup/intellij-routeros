package dev.dybug.routeros.device.debug

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import dev.dybug.routeros.device.RouterRestClient

/** The router side of a debug session: globals used as a rendezvous with the instrumented script. */
class RouterDebugController(private val client: RouterRestClient, val instrumenter: RosInstrumenter) {
    data class Pause(val line: Int, val variables: JsonObject)

    fun prepare(breakpointLines: Collection<Int>) {
        client.execute(
            ":global ${instrumenter.breakpointsVar} \"${breakpointList(breakpointLines)}\"; " +
                ":global ${instrumenter.stepVar} false; :global ${instrumenter.pauseVar} false; :global ${instrumenter.stateVar} \"\"",
        )
    }

    fun setBreakpoints(breakpointLines: Collection<Int>) {
        client.execute(":global ${instrumenter.breakpointsVar}; :set ${instrumenter.breakpointsVar} \"${breakpointList(breakpointLines)}\"")
    }

    /** The current pause, or null while the script runs. */
    fun pause(): Pause? {
        val environment = client.environment()
        if (environment[instrumenter.pauseVar] != "true") return null
        val state = environment[instrumenter.stateVar].orEmpty()
        return try {
            val json = JsonParser.parseString(state).asJsonObject
            Pause(json.get("line").asInt, json.getAsJsonObject("vars") ?: JsonObject())
        } catch (e: RuntimeException) {
            if (e is JsonSyntaxException || e is IllegalStateException || e is NullPointerException || e is UnsupportedOperationException) null else throw e
        }
    }

    fun resume(step: Boolean) {
        client.execute(
            ":global ${instrumenter.stepVar}; :global ${instrumenter.pauseVar}; " +
                ":set ${instrumenter.stepVar} $step; :set ${instrumenter.pauseVar} false",
        )
    }

    /** Removes the session's globals; best effort. */
    fun cleanUp() {
        runCatching {
            client.execute("/system script environment remove [find where name~\"^RosDbg${instrumenter.token}\"]")
        }
    }

    /** 0-based editor lines → `,1,5,` (1-based) as checked by the pause points. */
    private fun breakpointList(lines: Collection<Int>): String = lines.sorted().joinToString(",", prefix = ",", postfix = ",") { (it + 1).toString() }
}
