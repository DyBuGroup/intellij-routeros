package dev.dybug.routeros.schema

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiManager
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The schema used in a project: the bundled schema, merged with a schema synced from a router
 * (`.idea/routeros-schema/<version>.json.gz`) when one exists.
 */
@Service(Service.Level.PROJECT)
class RosProjectSchemaService(private val project: Project) {
    @Volatile
    private var cached: RosSchema? = null

    @Volatile
    private var loaded = false

    val schema: RosSchema?
        get() {
            if (!loaded) {
                synchronized(this) {
                    if (!loaded) {
                        cached = load()
                        loaded = true
                    }
                }
            }
            return cached ?: RosSchemaService.getInstance().schema
        }

    val syncedVersion: String? get() = syncedFile()?.name?.removeSuffix(".json.gz")

    fun store(version: String, nodes: JsonObject) {
        val directory = directory() ?: return
        require(Regex("[0-9][0-9A-Za-z.]*").matches(version)) { "unexpected RouterOS version '$version'" }
        directory.mkdirs()
        directory.listFiles { f -> f.name.endsWith(".json.gz") }?.forEach { it.delete() }
        val json = JsonObject().apply {
            addProperty("format", RosSchemaLoader.SUPPORTED_FORMAT)
            addProperty("version", version)
            addProperty("source", "router /console/inspect")
            add("nodes", nodes)
        }
        GZIPOutputStream(File(directory, "$version.json.gz").outputStream()).bufferedWriter().use { it.write(json.toString()) }
        reload()
    }

    fun reload() {
        synchronized(this) {
            loaded = false
            cached = null
        }
        PsiManager.getInstance(project).dropPsiCaches()
    }

    private fun load(): RosSchema? {
        val file = syncedFile() ?: return null
        return try {
            val synced = GZIPInputStream(file.inputStream()).reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }.asJsonObject
            RosSchemaLoader.loadBundled(merge = synced)
        } catch (e: Exception) {
            LOG.warn("Cannot load synced schema $file", e)
            null
        }
    }

    private fun directory(): File? {
        val base = project.basePath ?: project.guessProjectDir()?.path ?: return null
        return File(base, ".idea/routeros-schema")
    }

    private fun syncedFile(): File? = directory()?.listFiles { f -> f.name.endsWith(".json.gz") }?.maxByOrNull { it.lastModified() }

    companion object {
        private val LOG = logger<RosProjectSchemaService>()

        fun getInstance(project: Project): RosProjectSchemaService = project.getService(RosProjectSchemaService::class.java)
    }
}
