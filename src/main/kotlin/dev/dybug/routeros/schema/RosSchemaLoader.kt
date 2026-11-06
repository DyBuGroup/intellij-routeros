package dev.dybug.routeros.schema

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Reads the schema JSON written by tools/schema-extractor and the hand-written builtins.json. */
object RosSchemaLoader {
    const val SUPPORTED_FORMAT = 1

    fun loadBundled(version: String = BUNDLED_VERSION): RosSchema {
        val schema = resource("/schema/routeros-$version.json.gz").use { GZIPInputStream(it).reader(Charsets.UTF_8).use(JsonParser::parseReader) }
        val builtins = resource("/schema/builtins.json").use { it.reader(Charsets.UTF_8).use(JsonParser::parseReader) }
        return load(schema.asJsonObject, builtins.asJsonObject)
    }

    fun load(schemaJson: JsonObject, builtinsJson: JsonObject): RosSchema {
        val format = schemaJson.get("format")?.asInt ?: 0
        require(format == SUPPORTED_FORMAT) { "unsupported schema format $format" }
        val root = SchemaNode("", "", SchemaNode.Kind.IMPLICIT, null)
        val nodes = schemaJson.getAsJsonObject("nodes")
        for ((path, value) in nodes.entrySet().sortedBy { it.key }) {
            if (path.isEmpty()) continue
            val node = ensure(root, path.split('/'))
            val json = value.asJsonObject
            node.kind = when (json.str("kind")) {
                "dir" -> SchemaNode.Kind.DIRECTORY
                "settings" -> SchemaNode.Kind.SETTINGS
                "cmd" -> SchemaNode.Kind.COMMAND
                else -> SchemaNode.Kind.IMPLICIT
            }
            node.doc = json.str("doc")
            node.packageName = json.str("package")
            node.args = args(json.getAsJsonArray("args"))
            node.readOnly = args(json.getAsJsonArray("ro"))
            node.flags = args(json.getAsJsonArray("flags"))
        }
        return RosSchema(
            version = schemaJson.str("version") ?: "unknown",
            source = schemaJson.str("source") ?: "",
            root = root,
            builtins = builtins(builtinsJson),
        )
    }

    private fun ensure(root: SchemaNode, segments: List<String>): SchemaNode {
        var node = root
        for (segment in segments) {
            node = node.children.getOrPut(segment) {
                val path = if (node.path.isEmpty()) segment else node.path + "/" + segment
                SchemaNode(segment, path, SchemaNode.Kind.IMPLICIT, node)
            }
        }
        return node
    }

    private fun args(array: JsonArray?): Map<String, SchemaArg> {
        if (array == null) return emptyMap()
        val result = linkedMapOf<String, SchemaArg>()
        for (element in array) {
            val json = element.asJsonObject
            val name = json.str("n") ?: continue
            result[name] = SchemaArg(
                name = name,
                rawType = json.str("t").orEmpty(),
                doc = json.str("d"),
                mandatory = json.flag("m"),
                unsettable = json.flag("u"),
                deprecated = json.flag("x"),
                values = json.getAsJsonArray("values")?.map { it.asString }.orEmpty(),
                isBlock = json.get("block")?.asBoolean == true,
            )
        }
        return result
    }

    private fun builtins(json: JsonObject): Builtins {
        val scriptCommands = json.getAsJsonObject("scriptCommands").entrySet().associate { (name, value) ->
            val obj = value.asJsonObject
            name to ScriptCommand(
                name = name,
                syntax = obj.str("syntax").orEmpty(),
                doc = obj.str("doc"),
                params = args(obj.getAsJsonArray("params")),
                positionalValues = obj.getAsJsonArray("positionalValues")?.map { it.asString }.orEmpty(),
            )
        }
        val commandDocs = json.getAsJsonObject("commandDocs").entrySet().associate { (name, value) ->
            name to ((value.asJsonObject.str("syntax") ?: "") to (value.asJsonObject.str("doc") ?: ""))
        }
        val printParameters = json.getAsJsonObject("printParameters").entrySet().associate { (name, value) ->
            name to SchemaArg(name, if (name in VALUED_PRINT_PARAMETERS) "string" else "switch", value.asString.ifEmpty { null })
        }
        return Builtins(
            scriptCommands = scriptCommands,
            listCommands = json.getAsJsonArray("listCommands").map { it.asString },
            settingsCommands = json.getAsJsonArray("settingsCommands").map { it.asString },
            commandDocs = commandDocs,
            printParameters = printParameters,
            entryProperties = args(json.getAsJsonArray("entryProperties")),
            universalArguments = args(json.getAsJsonArray("universalArguments")),
        )
    }

    private fun resource(path: String): InputStream =
        RosSchemaLoader::class.java.getResourceAsStream(path) ?: error("missing resource $path")

    private fun JsonObject.str(key: String): String? = get(key)?.takeUnless(JsonElement::isJsonNull)?.asString

    private fun JsonObject.flag(key: String): Boolean = get(key)?.asInt == 1

    const val BUNDLED_VERSION = "7.26"

    private val VALUED_PRINT_PARAMETERS = setOf("file", "from", "interval", "order-by", "proplist", "where")
}
