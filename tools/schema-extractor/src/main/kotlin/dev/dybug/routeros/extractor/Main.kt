package dev.dybug.routeros.extractor

import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

private const val DEFAULT_SOURCE = "https://manual.mikrotik.com/llms-full.txt"

/**
 * Generates the bundled RouterOS schema from the official CLI reference.
 *
 * ```
 * ./gradlew :tools:schema-extractor:run --args="--version 7.26"
 * ```
 *
 * Options: `--version V` (required), `--source URL|FILE` (default: the manual's llms-full.txt),
 * `--out FILE`, `--overrides FILE`, `--cache DIR`, `--refresh` (ignore the cache).
 */
fun main(args: Array<String>) {
    val options = parseOptions(args)
    val version = options["version"] ?: usage("--version is required")
    require(Regex("[0-9][0-9a-z.]*").matches(version)) { "invalid version '$version'" }
    val source = options["source"] ?: DEFAULT_SOURCE
    val out = File(options["out"] ?: "src/main/resources/schema/routeros-$version.json.gz")
    val overrides = File(options["overrides"] ?: "src/main/resources/schema/overrides.json")
    val cacheDir = File(options["cache"] ?: "build/schema-cache/$version")
    val report = File(options["report"] ?: "tools/schema-extractor/reports/schema-report-$version.md")

    val text = load(source, cacheDir, refresh = "refresh" in options)
    val nodes = DocsParser.parse(text)
    if (nodes.size < 100) error("only ${nodes.size} reference pages found in $source; refusing to write a schema")
    val previous = SchemaWriter.readGzip(out)
    val overridesJson = if (overrides.isFile) JsonParser.parseString(overrides.readText()).asJsonObject else null
    SchemaWriter.writeGzip(SchemaWriter.toJson(version, source, nodes, overridesJson), out)
    report.parentFile.mkdirs()
    report.writeText(SchemaWriter.report(version, nodes, previous))
    println("Wrote ${nodes.size} nodes to $out; report: $report")
}

private fun load(source: String, cacheDir: File, refresh: Boolean): String {
    if (!source.startsWith("https://")) {
        require(!source.startsWith("http://")) { "plain HTTP sources are not allowed: $source" }
        return File(source).readText()
    }
    cacheDir.mkdirs()
    val cached = File(cacheDir, source.substringAfterLast('/').ifEmpty { "index" }.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    if (cached.isFile && !refresh) return cached.readText()
    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .proxy(java.net.ProxySelector.getDefault())
        .build()
    val request = HttpRequest.newBuilder(URI.create(source))
        .timeout(Duration.ofMinutes(5))
        .header("User-Agent", "routeros-intellij-schema-extractor")
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "GET $source returned HTTP ${response.statusCode()}" }
    check(response.uri().scheme == "https") { "redirected to a non-HTTPS URL: ${response.uri()}" }
    cached.writeText(response.body())
    return response.body()
}

private fun parseOptions(args: Array<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (!arg.startsWith("--")) usage("unexpected argument $arg")
        val key = arg.removePrefix("--")
        if (key == "refresh") {
            result[key] = "true"
            i++
        } else {
            result[key] = args.getOrNull(i + 1) ?: usage("missing value for $arg")
            i += 2
        }
    }
    return result
}

private fun usage(message: String): Nothing {
    System.err.println("error: $message")
    System.err.println("usage: --version V [--source URL|FILE] [--out FILE] [--overrides FILE] [--cache DIR] [--report FILE] [--refresh]")
    exitProcess(2)
}
