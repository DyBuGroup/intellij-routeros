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
 * Generates the bundled RouterOS schemas from the official CLI reference.
 *
 * ```
 * ./gradlew :tools:schema-extractor:run --args="--channels"        # one schema per release channel
 * ./gradlew :tools:schema-extractor:run --args="--version 7.26"    # a single version
 * ```
 *
 * `--channels` asks upgrade.mikrotik.com for the current stable, long-term and development
 * releases, generates a schema for each (from the manual's per-version docs), writes
 * `schema/index.json` and removes schemas no channel uses any more.
 *
 * Options for `--version`: `--source URL|FILE` (default: the manual's llms-full.txt), `--out FILE`,
 * `--overrides FILE`, `--cache DIR`, `--report FILE`, `--refresh` (ignore the cache).
 */
fun main(args: Array<String>) {
    val options = parseOptions(args)
    if ("channels" in options) {
        Channels.update(File(options["schema-dir"] ?: "src/main/resources/schema"), refresh = "refresh" in options)
        return
    }
    val version = options["version"] ?: usage("--version or --channels is required")
    generate(
        version = version,
        source = options["source"] ?: DEFAULT_SOURCE,
        out = options["out"]?.let(::File),
        overrides = options["overrides"]?.let(::File),
        cacheDir = options["cache"]?.let(::File),
        report = options["report"]?.let(::File),
        refresh = "refresh" in options,
    )
}

/** Generates one schema; returns the number of reference pages. */
fun generate(
    version: String,
    source: String,
    out: File? = null,
    overrides: File? = null,
    cacheDir: File? = null,
    report: File? = null,
    refresh: Boolean = false,
    previous: File? = null,
): Int {
    require(Regex("[0-9][0-9a-z.]*").matches(version)) { "invalid version '$version'" }
    val outFile = out ?: File("src/main/resources/schema/routeros-$version.json.gz")
    val overridesFile = overrides ?: File("src/main/resources/schema/overrides.json")
    val reportFile = report ?: File("tools/schema-extractor/reports/schema-report-$version.md")
    val text = load(source, cacheDir ?: File("build/schema-cache/$version"), refresh)
    val nodes = DocsParser.parse(text)
    if (nodes.size < 100) error("only ${nodes.size} reference pages found in $source; refusing to write a schema")
    val previousSchema = SchemaWriter.readGzip(previous ?: outFile)
    val overridesJson = if (overridesFile.isFile) JsonParser.parseString(overridesFile.readText()).asJsonObject else null
    SchemaWriter.writeGzip(SchemaWriter.toJson(version, source, nodes, overridesJson), outFile)
    reportFile.absoluteFile.parentFile?.mkdirs()
    reportFile.writeText(SchemaWriter.report(version, nodes, previousSchema))
    println("Wrote ${nodes.size} nodes to $outFile; report: $reportFile")
    return nodes.size
}

/** Fetches a small HTTPS text resource (channel versions, the manual index). */
internal fun fetchText(url: String, cacheDir: File, refresh: Boolean): String = load(url, cacheDir, refresh)

private const val MAX_SOURCE_BYTES = 64L * 1024 * 1024

private fun readLimited(file: File): String {
    require(file.length() <= MAX_SOURCE_BYTES) { "$file is larger than $MAX_SOURCE_BYTES bytes" }
    return file.readText()
}

private fun load(source: String, cacheDir: File, refresh: Boolean): String {
    if (!source.startsWith("https://")) {
        require(!source.startsWith("http://")) { "plain HTTP sources are not allowed: $source" }
        return readLimited(File(source))
    }
    cacheDir.mkdirs()
    val key = java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(16)
    val cached = File(cacheDir, "$key-" + source.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(64))
    if (cached.isFile && !refresh) return readLimited(cached)
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
    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    response.body().use { body ->
        check(response.statusCode() == 200) { "GET $source returned HTTP ${response.statusCode()}" }
        check(response.uri().scheme == "https") { "redirected to a non-HTTPS URL: ${response.uri()}" }
        // the request timeout covers only the headers; close the stream if the body stalls
        val watchdog = java.util.Timer(true).apply { schedule(object : java.util.TimerTask() {
            override fun run() { runCatching { body.close() } }
        }, 10 * 60 * 1000L) }
        val bytes = try {
            body.readNBytes((MAX_SOURCE_BYTES + 1).toInt())
        } finally {
            watchdog.cancel()
        }
        check(bytes.size <= MAX_SOURCE_BYTES) { "response from $source exceeds $MAX_SOURCE_BYTES bytes" }
        val text = String(bytes, Charsets.UTF_8)
        cached.writeText(text)
        return text
    }
}

private fun parseOptions(args: Array<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (!arg.startsWith("--")) usage("unexpected argument $arg")
        val key = arg.removePrefix("--")
        if (key == "refresh" || key == "channels") {
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
    System.err.println("usage: --channels [--schema-dir DIR] [--refresh]")
    System.err.println("       --version V [--source URL|FILE] [--out FILE] [--overrides FILE] [--cache DIR] [--report FILE] [--refresh]")
    exitProcess(2)
}
