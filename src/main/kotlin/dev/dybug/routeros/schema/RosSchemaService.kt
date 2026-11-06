package dev.dybug.routeros.schema

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger

/**
 * Application-wide access to the RouterOS schema. The bundled schema is loaded lazily on first
 * use (≈ 0.3 MB compressed); callers run in read actions on background threads.
 */
@Service(Service.Level.APP)
class RosSchemaService {
    private val bundled: RosSchema? by lazy {
        try {
            RosSchemaLoader.loadBundled()
        } catch (e: Exception) {
            LOG.error("Cannot load the bundled RouterOS schema", e)
            null
        }
    }

    /** The active schema, or null when it could not be loaded (features then degrade silently). */
    val schema: RosSchema? get() = bundled

    companion object {
        private val LOG = logger<RosSchemaService>()

        fun getInstance(): RosSchemaService = service()

        fun schema(): RosSchema? = getInstance().schema
    }
}
