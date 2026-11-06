package dev.dybug.routeros.schema

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The schema used in a project: the bundled schema, merged with a schema synced from a router
 * (`.idea/routeros-schema/<version>.json.gz`) when one exists. The file is re-checked at most
 * every few seconds, so a checkout or deletion is picked up without a restart.
 */
@Service(Service.Level.PROJECT)
class RosProjectSchemaService(private val project: Project) {
    private class Snapshot(val file: File?, val stamp: Long, val schema: RosSchema?)

    @Volatile
    private var snapshot: Snapshot? = null

    @Volatile
    private var checkedAt = 0L

    val schema: RosSchema?
        get() = current().schema ?: RosSchemaService.getInstance().schema

    val syncedVersion: String? get() = current().file?.name?.removeSuffix(".json.gz")

    private fun current(): Snapshot {
        val now = System.currentTimeMillis()
        val known = snapshot
        if (known != null && now - checkedAt < RECHECK_MILLIS) return known
        synchronized(this) {
            checkedAt = now
            val file = syncedFile()
            val stamp = file?.let { it.lastModified() xor it.length() } ?: 0L
            val previous = snapshot
            if (previous != null && previous.file == file && previous.stamp == stamp) return previous
            val fresh = Snapshot(file, stamp, file?.let(::load))
            snapshot = fresh
            if (previous != null) invalidateAnalysis()
            return fresh
        }
    }

    /** Stores a synced schema: written to a temporary file and moved into place atomically. */
    @Synchronized
    fun store(version: String, nodes: JsonObject) {
        require(Regex("[0-9][0-9A-Za-z.]*").matches(version)) { "unexpected RouterOS version '$version'" }
        val directory = safeDirectory(create = true) ?: throw IOException("Cannot use .idea/routeros-schema in this project")
        val json = JsonObject().apply {
            addProperty("format", RosSchemaLoader.SUPPORTED_FORMAT)
            addProperty("version", version)
            addProperty("source", "router /console/inspect")
            add("nodes", nodes)
        }
        val target = File(directory, "$version.json.gz")
        val temporary = File.createTempFile("schema-", ".tmp", directory)
        try {
            GZIPOutputStream(temporary.outputStream()).bufferedWriter().use { it.write(json.toString()) }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
        directory.listFiles { f -> f.name.endsWith(".json.gz") && f.name != target.name }?.forEach { it.delete() }
        checkedAt = 0L
        current()
    }

    private fun invalidateAnalysis() {
        ApplicationManager.getApplication().invokeLater({
            PsiManager.getInstance(project).dropPsiCaches()
            com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart()
        }, project.disposed)
    }

    private fun load(file: File): RosSchema? = try {
        if (file.length() > MAX_COMPRESSED_BYTES) throw IOException("synced schema is too large")
        val synced = BoundedInputStream(GZIPInputStream(file.inputStream()), MAX_DECOMPRESSED_BYTES)
            .reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }.asJsonObject
        RosSchemaLoader.loadBundled(merge = synced)
    } catch (e: Exception) {
        LOG.warn("Cannot load synced schema $file", e)
        null
    } catch (e: StackOverflowError) {
        LOG.warn("Synced schema $file is nested too deeply", e)
        null
    }

    /**
     * `<project>/.idea/routeros-schema`, refusing symlinks anywhere below the project directory so
     * that writes and deletions cannot escape the project.
     */
    private fun safeDirectory(create: Boolean): File? {
        val base = project.basePath?.let(::File) ?: return null
        val idea = File(base, ".idea")
        val directory = File(idea, "routeros-schema")
        for (part in listOf(idea, directory)) {
            if (Files.isSymbolicLink(part.toPath())) return null
        }
        if (!directory.exists()) {
            if (!create) return null
            directory.mkdirs()
        }
        val canonical = directory.canonicalFile
        if (!canonical.toPath().startsWith(base.canonicalFile.toPath())) return null
        if (!Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        return directory
    }

    private fun syncedFile(): File? = safeDirectory(create = false)
        ?.listFiles { f -> f.name.endsWith(".json.gz") && !Files.isSymbolicLink(f.toPath()) && f.isFile }
        ?.maxByOrNull { it.lastModified() }

    private class BoundedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) check(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) check(it.toLong()) }

        private fun check(n: Long) {
            count += n
            if (count > limit) throw IOException("synced schema exceeds $limit bytes when decompressed")
        }
    }

    companion object {
        private val LOG = logger<RosProjectSchemaService>()
        private const val RECHECK_MILLIS = 5_000L
        private const val MAX_COMPRESSED_BYTES = 16L * 1024 * 1024
        private const val MAX_DECOMPRESSED_BYTES = 64L * 1024 * 1024

        fun getInstance(project: Project): RosProjectSchemaService = project.getService(RosProjectSchemaService::class.java)
    }
}
