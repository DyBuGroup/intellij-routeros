package dev.dybug.routeros.schema

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-wide access to the bundled RouterOS schemas (one per release channel). Each version
 * is loaded lazily on first use (≈ 0.3 MB compressed); callers run in read actions on background threads.
 */
@Service(Service.Level.APP)
class RosSchemaService {
    private val loaded = ConcurrentHashMap<String, Result<RosSchema>>()

    /** The default (stable) schema, or null when it could not be loaded (features then degrade silently). */
    val schema: RosSchema? get() = bundled(RosSchemaLoader.defaultVersion)

    fun bundled(version: String): RosSchema? = loaded.computeIfAbsent(version) {
        runCatching { RosSchemaLoader.loadBundled(version) }.onFailure { LOG.error("Cannot load the bundled RouterOS $version schema", it) }
    }.getOrNull()

    companion object {
        private val LOG = logger<RosSchemaService>()

        fun getInstance(): RosSchemaService = service()

        fun schema(): RosSchema? = getInstance().schema

        /** The project's schema: the selected bundled version, merged with a schema synced from a router if there is one. */
        fun schema(project: com.intellij.openapi.project.Project?): RosSchema? =
            if (project == null || project.isDisposed) getInstance().schema else RosProjectSchemaService.getInstance(project).schema
    }
}
