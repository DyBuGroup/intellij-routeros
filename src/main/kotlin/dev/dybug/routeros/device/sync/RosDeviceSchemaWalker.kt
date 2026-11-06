package dev.dybug.routeros.device.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressIndicator
import dev.dybug.routeros.device.RouterRestClient

/**
 * Builds a schema (same JSON format as the bundled one, without descriptions or types) from a
 * router's `/console/inspect request=child`. To keep the request count moderate it lists the
 * arguments of `add`/`set` and of menu-specific commands only.
 */
class RosDeviceSchemaWalker(
    private val client: RouterRestClient,
    private val skipRootCommands: Set<String>,
    private val maxNodes: Int = 20_000,
) {
    private val nodes = JsonObject()
    private var requests = 0

    fun walk(indicator: ProgressIndicator?): JsonObject {
        visitMenu(emptyList(), indicator)
        return nodes
    }

    val requestCount: Int get() = requests

    private fun visitMenu(path: List<String>, indicator: ProgressIndicator?) {
        indicator?.checkCanceled()
        if (nodes.size() >= maxNodes || path.size > 10) return
        indicator?.text2 = "/" + path.joinToString("/")
        val children = children(path)
        val commands = children.filter { it.second == "cmd" }.map { it.first }.toSet()
        if (path.isNotEmpty()) {
            val node = JsonObject()
            node.addProperty("kind", if ("add" in commands) "dir" else "settings")
            val argsFrom = when {
                "add" in commands -> "add"
                "set" in commands -> "set"
                else -> null
            }
            argsFrom?.let { node.add("args", args(path + it)) }
            nodes.add(path.joinToString("/"), node)
        }
        for ((name, type) in children) {
            when (type) {
                "dir", "path" -> visitMenu(path + name, indicator)
                "cmd" -> if (name !in STANDARD_COMMANDS && !(path.isEmpty() && name in skipRootCommands)) {
                    indicator?.checkCanceled()
                    val node = JsonObject()
                    node.addProperty("kind", "cmd")
                    node.add("args", args(path + name))
                    nodes.add((path + name).joinToString("/"), node)
                }
            }
        }
    }

    private fun args(path: List<String>): JsonArray = JsonArray().apply {
        children(path).filter { it.second == "arg" }.forEach { (name, _) ->
            add(JsonObject().apply { addProperty("n", name) })
        }
    }

    private fun children(path: List<String>): List<Pair<String, String>> {
        requests++
        return client.inspectChildren(path)
    }

    companion object {
        val STANDARD_COMMANDS = setOf(
            "add", "comment", "disable", "edit", "enable", "export", "find", "get", "move", "print", "remove", "reset", "set", "unset",
        )
    }
}
