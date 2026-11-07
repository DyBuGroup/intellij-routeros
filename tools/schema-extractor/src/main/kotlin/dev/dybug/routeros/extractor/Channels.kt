package dev.dybug.routeros.extractor

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File

/** RouterOS release channels and the manual versions that document them. */
object Channels {
    /** Channels bundled with the plugin, in display order; the first one is the default. */
    val BUNDLED = listOf("stable", "long-term", "development")

    private const val UPGRADE = "https://upgrade.mikrotik.com/routeros/NEWESTa7."
    private const val MANUAL = "https://manual.mikrotik.com"

    /** [docsVersion]: the manual version used, normally the release's major.minor (`7.26beta5` → `7.26`). */
    data class Release(val channel: String, val release: String, val docsVersion: String = docsVersionOf(release))

    /**
     * The manual documents a release once it exists; a development release ahead of the manual
     * (`7.27beta1` while the manual is at 7.26) uses the newest manual instead.
     */
    fun docsVersionFor(release: String, currentDocsVersion: String): String {
        val wanted = docsVersionOf(release)
        return if (compare(wanted, currentDocsVersion) > 0) currentDocsVersion else wanted
    }

    private fun compare(a: String, b: String): Int {
        val left = a.split('.').map(String::toInt)
        val right = b.split('.').map(String::toInt)
        return compareValuesBy(left, right, { it[0] }, { it[1] })
    }

    fun docsVersionOf(release: String): String =
        Regex("""^(\d+\.\d+)""").find(release)?.groupValues?.get(1) ?: error("unexpected RouterOS version '$release'")

    /** `NEWESTa7.stable` answers `7.25.1 1793623193`. */
    fun parseNewest(text: String): String {
        val version = text.trim().substringBefore(' ')
        require(Regex("""\d+\.\d+[0-9a-z.]*""").matches(version)) { "unexpected channel answer '$text'" }
        return version
    }

    /** The unversioned manual documents the newest release: "Index of the RouterOS 7.26 manual". */
    fun parseCurrentDocsVersion(llmsIndex: String): String =
        Regex("""Index of the RouterOS (\d+\.\d+) manual""").find(llmsIndex)?.groupValues?.get(1)
            ?: error("cannot find the manual version in llms.txt")

    fun docsSource(docsVersion: String, currentDocsVersion: String): String =
        if (docsVersion == currentDocsVersion) "$MANUAL/llms-full.txt" else "$MANUAL/docs/$docsVersion/llms-full.txt"

    fun update(schemaDir: File, refresh: Boolean) {
        val cache = File("build/schema-cache/channels")
        // channel answers change daily: never cache them
        val current = parseCurrentDocsVersion(fetchText("$MANUAL/llms.txt", cache, refresh = true))
        val releases = BUNDLED.map {
            val release = parseNewest(fetchText(UPGRADE + it, File(cache, it), refresh = true))
            Release(it, release, docsVersionFor(release, current))
        }
        releases.forEach { println("${it.channel}: RouterOS ${it.release} → docs ${it.docsVersion}") }

        // generate everything into a staging directory first: a failure leaves the schemas untouched
        val staging = File("build/schema-staging").apply { deleteRecursively(); mkdirs() }
        val versions = releases.map { it.docsVersion }.distinct()
        for (version in versions) {
            generate(
                version = version,
                source = docsSource(version, current),
                out = File(staging, "routeros-$version.json.gz"),
                overrides = File(schemaDir, "overrides.json"),
                refresh = refresh,
                // compare against the schema currently in the repository
                previous = File(schemaDir, "routeros-$version.json.gz"),
            )
        }
        for (version in versions) {
            File(staging, "routeros-$version.json.gz").copyTo(File(schemaDir, "routeros-$version.json.gz"), overwrite = true)
        }
        File(schemaDir, "index.json").writeText(indexJson(releases))
        staging.deleteRecursively()

        val used = versions.toSet()
        schemaDir.listFiles { f -> f.name.startsWith("routeros-") && f.name.endsWith(".json.gz") }
            ?.filter { it.name.removePrefix("routeros-").removeSuffix(".json.gz") !in used }
            ?.forEach {
                println("Removing ${it.name}: no channel uses it")
                it.delete()
            }
        File(REPORTS).listFiles { f -> f.name.startsWith("schema-report-") && f.name.endsWith(".md") }
            ?.filter { it.name.removePrefix("schema-report-").removeSuffix(".md") !in used }
            ?.forEach { it.delete() }
    }

    private const val REPORTS = "tools/schema-extractor/reports"

    fun indexJson(releases: List<Release>): String {
        val channels = JsonObject()
        for (release in releases) {
            channels.add(
                release.channel,
                JsonObject().apply {
                    addProperty("version", release.docsVersion)
                    addProperty("release", release.release)
                },
            )
        }
        val root = JsonObject().apply {
            addProperty("format", 1)
            addProperty("default", releases.first().channel)
            add("channels", channels)
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n"
    }
}
