package dev.dybug.routeros.extractor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.dybug.routeros.schema.ValueType
import java.io.File
import java.util.zip.GZIPOutputStream

/** Writes the schema JSON consumed by the plugin (`RosSchemaLoader`) and a human-readable report. */
object SchemaWriter {
    const val FORMAT = 1

    fun toJson(version: String, source: String, nodes: List<DocNode>, overrides: JsonObject?): JsonObject {
        val root = JsonObject()
        root.addProperty("format", FORMAT)
        root.addProperty("version", version)
        root.addProperty("source", source)
        val nodesJson = JsonObject()
        for (node in nodes.sortedBy { it.path }) {
            nodesJson.add(node.path, nodeJson(node))
        }
        overrides?.getAsJsonObject("nodes")?.entrySet()?.forEach { (path, patch) ->
            val existing = nodesJson.getAsJsonObject(path)
            if (existing == null) {
                nodesJson.add(path, patch)
            } else {
                patch.asJsonObject.entrySet().forEach { (key, value) -> existing.add(key, value) }
            }
        }
        root.add("nodes", nodesJson)
        return root
    }

    private fun nodeJson(node: DocNode): JsonObject = JsonObject().apply {
        addProperty("kind", node.kind.json)
        node.doc?.let { addProperty("doc", it) }
        node.meta["package"]?.let { addProperty("package", it) }
        node.meta["syscap"]?.let { addProperty("syscap", it) }
        node.meta["conditions"]?.let { addProperty("conditions", it) }
        if (node.args.isNotEmpty()) add("args", argsJson(node.args))
        if (node.readOnly.isNotEmpty()) add("ro", argsJson(node.readOnly))
        if (node.flags.isNotEmpty()) add("flags", argsJson(node.flags))
    }

    private fun argsJson(args: List<DocArg>): JsonArray = JsonArray().apply {
        for (arg in args) {
            add(
                JsonObject().apply {
                    addProperty("n", arg.name)
                    if (arg.type.isNotEmpty()) addProperty("t", arg.type)
                    arg.doc?.let { addProperty("d", it) }
                    if (arg.mandatory) addProperty("m", 1)
                    if (arg.unset) addProperty("u", 1)
                    if (arg.deprecated) addProperty("x", 1)
                    arg.syscap?.let { addProperty("s", it) }
                },
            )
        }
    }

    fun writeGzip(json: JsonObject, file: File) {
        file.parentFile.mkdirs()
        GZIPOutputStream(file.outputStream()).bufferedWriter().use { it.write(json.toString()) }
    }

    fun report(version: String, nodes: List<DocNode>, previous: JsonObject?): String {
        val args = nodes.flatMap { it.args + it.readOnly }
        val unknown = args.filter { ValueType.parse(it.type) is ValueType.Unknown && it.type.isNotEmpty() }
        val sb = StringBuilder()
        sb.appendLine("# Schema report — RouterOS $version")
        sb.appendLine()
        sb.appendLine("| Item | Count |")
        sb.appendLine("|------|-------|")
        sb.appendLine("| Directories | ${nodes.count { it.kind == DocNode.Kind.DIRECTORY }} |")
        sb.appendLine("| Settings directories | ${nodes.count { it.kind == DocNode.Kind.SETTINGS }} |")
        sb.appendLine("| Commands | ${nodes.count { it.kind == DocNode.Kind.COMMAND }} |")
        sb.appendLine("| Arguments (settable) | ${nodes.sumOf { it.args.size }} |")
        sb.appendLine("| Arguments (read-only) | ${nodes.sumOf { it.readOnly.size }} |")
        sb.appendLine("| Types not understood | ${unknown.size} |")
        if (previous != null) {
            val before = previous.getAsJsonObject("nodes").keySet()
            val after = nodes.map { it.path }.toSet()
            sb.appendLine()
            sb.appendLine("## Diff against previous schema (${previous.get("version")?.asString})")
            sb.appendLine()
            sb.appendLine("Added: ${(after - before).sorted().joinToString(", ").ifEmpty { "none" }}")
            sb.appendLine()
            sb.appendLine("Removed: ${(before - after).sorted().joinToString(", ").ifEmpty { "none" }}")
        }
        if (unknown.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Type strings not understood (top 50)")
            sb.appendLine()
            unknown.groupingBy { it.type.substringBefore(' ') }.eachCount().entries.sortedByDescending { it.value }.take(50)
                .forEach { sb.appendLine("- `${it.key}` × ${it.value}") }
        }
        return sb.toString()
    }

    fun readGzip(file: File): JsonObject? {
        if (!file.isFile) return null
        return java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    }
}
