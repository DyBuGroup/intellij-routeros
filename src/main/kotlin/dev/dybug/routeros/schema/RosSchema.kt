package dev.dybug.routeros.schema

/** The RouterOS command tree for one RouterOS version, plus the built-in script commands. */
class RosSchema(
    val version: String,
    val source: String,
    val root: SchemaNode,
    val builtins: Builtins,
) {
    fun node(segments: List<String>): SchemaNode? {
        var node = root
        for (segment in segments) node = node.children[segment] ?: return null
        return node
    }

    /**
     * Splits a resolved path such as `[ip, address, add]` into the menu and the command word.
     * Command pages (`tool/fetch`) are their own target.
     */
    fun resolveCommand(segments: List<String>): CommandTarget {
        var node = root
        var index = 0
        while (index < segments.size) {
            if (node.kind == SchemaNode.Kind.COMMAND) break
            node = node.children[segments[index]] ?: break
            index++
        }
        // words after the command word are positional arguments: /ip address print detail, /system script run x
        return when {
            node.kind == SchemaNode.Kind.COMMAND -> CommandTarget(node.parent ?: root, node.name, node, consumed = index)
            index == segments.size -> CommandTarget(node, null, null, consumed = index)
            segments[index] in commandsOf(node) -> CommandTarget(node, segments[index], null, consumed = index + 1)
            else -> CommandTarget(node, null, null, consumed = index, unknownSegment = segments[index])
        }
    }

    /** Command words available in a menu: standard commands by menu kind plus documented command pages. */
    fun commandsOf(node: SchemaNode): Set<String> {
        val standard = when (node.kind) {
            SchemaNode.Kind.DIRECTORY -> builtins.listCommands
            SchemaNode.Kind.SETTINGS -> builtins.settingsCommands
            else -> emptyList()
        }
        return standard.toSet() + node.children.values.filter { it.kind == SchemaNode.Kind.COMMAND }.map { it.name }
    }

    /** Arguments accepted by `verb` in `menu`, or by the command page itself. */
    fun argumentsOf(target: CommandTarget): Map<String, SchemaArg> {
        target.command?.let { return it.args }
        val menu = target.menu ?: return emptyMap()
        return when (target.verb) {
            "add" -> menu.args + entryProperties(menu, add = true)
            "set" -> menu.args + entryProperties(menu, add = false) + universal("numbers")
            "find", "get" -> menu.args + menu.readOnly + entryProperties(menu, add = false) + flagArgs(menu)
            "print" -> builtins.printParameters
            else -> emptyMap()
        }
    }

    private fun universal(vararg names: String): Map<String, SchemaArg> =
        names.mapNotNull { builtins.universalArguments[it] }.associateBy { it.name }

    private fun entryProperties(menu: SchemaNode, add: Boolean): Map<String, SchemaArg> {
        if (menu.kind != SchemaNode.Kind.DIRECTORY) return emptyMap()
        return builtins.entryProperties.filterKeys { add || (it != "copy-from" && it != "place-before") }
    }

    private fun flagArgs(menu: SchemaNode): Map<String, SchemaArg> =
        // flag rows: arg = letter (X), typ = flag name (disabled), description
        menu.flags.values.filter { it.rawType.isNotEmpty() }.associate { flag ->
            flag.rawType to SchemaArg(flag.rawType, "bool", "Flag ${flag.name}: ${flag.doc.orEmpty()}")
        }

    data class CommandTarget(
        val menu: SchemaNode?,
        val verb: String?,
        val command: SchemaNode?,
        val consumed: Int,
        val unknownSegment: String? = null,
    ) {
        val isKnown: Boolean get() = unknownSegment == null
    }
}

class SchemaNode(
    val name: String,
    val path: String,
    var kind: Kind,
    val parent: SchemaNode?,
) {
    enum class Kind { IMPLICIT, DIRECTORY, SETTINGS, COMMAND }

    var doc: String? = null
    var packageName: String? = null
    var args: Map<String, SchemaArg> = emptyMap()
    var readOnly: Map<String, SchemaArg> = emptyMap()
    var flags: Map<String, SchemaArg> = emptyMap()
    val children: MutableMap<String, SchemaNode> = linkedMapOf()

    /** URL of the CLI reference page; segments are URL-encoded so schema text cannot break out of an attribute. */
    val docUrl: String
        get() = "https://manual.mikrotik.com/docs/cli-reference/" +
            path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }

    val isMenu: Boolean get() = kind != Kind.COMMAND

    override fun toString(): String = "/$path ($kind)"
}

class SchemaArg(
    val name: String,
    val rawType: String,
    val doc: String?,
    val mandatory: Boolean = false,
    val unsettable: Boolean = false,
    val deprecated: Boolean = false,
    /** Values given explicitly (script command arguments) when there is no type string. */
    val values: List<String> = emptyList(),
    val isBlock: Boolean = false,
) {
    val type: ValueType by lazy {
        if (values.isNotEmpty()) ValueType.Enum(values, dynamic = false) else ValueType.parse(rawType)
    }
}

class ScriptCommand(
    val name: String,
    val syntax: String,
    val doc: String?,
    val params: Map<String, SchemaArg>,
    val positionalValues: List<String>,
)

class Builtins(
    val scriptCommands: Map<String, ScriptCommand>,
    val listCommands: List<String>,
    val settingsCommands: List<String>,
    val commandDocs: Map<String, Pair<String, String>>,
    val printParameters: Map<String, SchemaArg>,
    val entryProperties: Map<String, SchemaArg>,
    /** Arguments accepted by many commands (numbers=, as-value, once, interval=…); never reported as unknown. */
    val universalArguments: Map<String, SchemaArg> = emptyMap(),
)
