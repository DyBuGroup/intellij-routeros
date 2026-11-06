package dev.dybug.routeros.extractor

/** One CLI reference page: a menu (directory) or a command. */
data class DocNode(
    val path: String,
    val kind: Kind,
    val doc: String?,
    val args: List<DocArg>,
    val readOnly: List<DocArg>,
    val flags: List<DocArg>,
    val meta: Map<String, String> = emptyMap(),
) {
    enum class Kind(val json: String) { DIRECTORY("dir"), SETTINGS("settings"), COMMAND("cmd") }
}

data class DocArg(
    val name: String,
    val type: String,
    val doc: String?,
    val mandatory: Boolean = false,
    val unset: Boolean = false,
    val deprecated: Boolean = false,
    val syscap: String? = null,
)

/**
 * Parses CLI reference pages as published in `llms-full.txt` or as individual `.md` pages:
 *
 * ```
 * ## ip/address
 * **Type:** Directory
 * <ArgTable c1="Argument" …>
 * <ArgTableRow arg="interface" typ="iface_enum" mandatory="1">Description</ArgTableRow>
 * ```
 */
object DocsParser {
    private val HEADING = Regex("""^## ([a-z0-9][a-z0-9/._+\-]*)\s*$""")
    private val META = Regex("""^\*\*([A-Za-z -]+):\*\*\s*(.*?)\s*$""")
    private val TYPE = Regex("""^\*\*Type:\*\*\s*(Directory|Settings Directory|Command)\s*$""")
    private val TABLE = Regex("""<ArgTable\s+c1="([^"]*)"[^>]*>(.*?)</ArgTable>""", RegexOption.DOT_MATCHES_ALL)
    private const val MAX_ROW = 64 * 1024
    private const val MAX_ATTRIBUTES = 32
    private val ATTR = Regex("""([a-z]+)="([^"]*)"""")

    fun parse(text: String): List<DocNode> {
        val lines = text.lines()
        val result = mutableListOf<DocNode>()
        var i = 0
        while (i < lines.size) {
            val heading = HEADING.matchEntire(lines[i])
            if (heading == null) {
                i++
                continue
            }
            // metadata lines such as **Syscap:**, **Package:**, **Conditions:** may precede **Type:**
            val meta = mutableMapOf<String, String>()
            var typeIndex = i + 1
            var typeLine: MatchResult? = null
            while (typeIndex < lines.size && typeIndex <= i + 6) {
                typeLine = TYPE.matchEntire(lines[typeIndex])
                if (typeLine != null) break
                val m = META.matchEntire(lines[typeIndex]) ?: break
                meta[m.groupValues[1].lowercase()] = m.groupValues[2]
                typeIndex++
            }
            if (typeLine == null) {
                i++
                continue
            }
            var end = typeIndex + 1
            while (end < lines.size && lines[end].trim() != "---" && !HEADING.matches(lines[end])) end++
            val body = lines.subList(typeIndex + 1, end).joinToString("\n")
            result += parseSection(heading.groupValues[1], typeLine.groupValues[1], body).copy(meta = meta)
            i = end
        }
        return result
    }

    private fun parseSection(path: String, type: String, body: String): DocNode {
        val kind = when (type) {
            "Directory" -> DocNode.Kind.DIRECTORY
            "Settings Directory" -> DocNode.Kind.SETTINGS
            else -> DocNode.Kind.COMMAND
        }
        val firstTable = body.indexOf("<ArgTable")
        val doc = cleanDoc(if (firstTable >= 0) body.substring(0, firstTable) else body)
        val args = mutableListOf<DocArg>()
        val readOnly = mutableListOf<DocArg>()
        val flags = mutableListOf<DocArg>()
        for (table in TABLE.findAll(body)) {
            val target = when (table.groupValues[1]) {
                "Flag" -> flags
                "Read-only Argument" -> readOnly
                else -> args
            }
            for ((attributes, description) in rows(table.groupValues[2])) {
                val attrs = ATTR.findAll(attributes).take(MAX_ATTRIBUTES)
                    .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
                val name = attrs["arg"]?.trim().orEmpty()
                if (name.isEmpty()) continue
                target += DocArg(
                    name = name,
                    type = attrs["typ"].orEmpty().replace(Regex("\\s+"), " ").trim(),
                    doc = cleanDoc(description),
                    mandatory = attrs["mandatory"] == "1",
                    unset = attrs["unset"] == "1",
                    deprecated = attrs["deprecated"] == "1",
                    syscap = attrs["syscap"]?.takeIf { it.isNotBlank() },
                )
            }
        }
        return DocNode(path, kind, doc, args, readOnly, flags)
    }

    /**
     * `<ArgTableRow attrs>description</ArgTableRow>` pairs, scanned iteratively: attribute values may
     * contain `>` (`typ="enum (<- | ->)"`), and regex repetition over attributes can overflow the stack.
     */
    private fun rows(body: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var index = 0
        while (true) {
            val start = body.indexOf("<ArgTableRow", index)
            if (start < 0) return result
            var pos = start + "<ArgTableRow".length
            var quoted = false
            while (pos < body.length && (quoted || body[pos] != '>')) {
                if (body[pos] == '"') quoted = !quoted
                pos++
            }
            if (pos >= body.length) return result
            val end = body.indexOf("</ArgTableRow>", pos)
            if (end < 0) return result
            if (end - start <= MAX_ROW) result += body.substring(start + "<ArgTableRow".length, pos) to body.substring(pos + 1, end)
            index = end + "</ArgTableRow>".length
        }
    }

    private fun cleanDoc(raw: String): String? {
        val text = unescape(raw).lines().joinToString("\n") { it.trimEnd() }.trim()
            .replace(Regex("\n{3,}"), "\n\n")
        if (text.isEmpty()) return null
        return if (text.length > MAX_DOC) text.take(MAX_DOC).substringBeforeLast(' ') + " …" else text
    }

    private fun unescape(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

    private const val MAX_DOC = 3000
}
