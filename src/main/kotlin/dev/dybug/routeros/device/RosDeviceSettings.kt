package dev.dybug.routeros.device

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection

/** Project-level router connection profiles, stored in `.idea/routeros.xml` (never the passwords). */
@Service(Service.Level.PROJECT)
@State(name = "RouterOSDevices", storages = [Storage("routeros.xml")])
class RosDeviceSettings(private val project: Project) : PersistentStateComponent<RosDeviceSettings.State> {
    class State {
        @XCollection(style = XCollection.Style.v2)
        var profiles: MutableList<RouterProfile> = mutableListOf()
        var defaultProfile: String = ""

        /** Bundled schema version to use, or [AUTO_SCHEMA]: match the router, else the stable channel. */
        var schemaVersion: String = AUTO_SCHEMA
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    val profiles: List<RouterProfile> get() = state.profiles

    /** The named profile; the default (or only) profile when [name] is null or empty. Unknown names give null. */
    fun profile(name: String?): RouterProfile? {
        if (!name.isNullOrEmpty()) return state.profiles.firstOrNull { it.name == name }
        return state.profiles.firstOrNull { it.name == state.defaultProfile } ?: state.profiles.firstOrNull()
    }

    fun update(profiles: List<RouterProfile>, defaultProfile: String) {
        state.profiles = profiles.map { it.copy() }.toMutableList()
        state.defaultProfile = defaultProfile
    }

    fun rememberVersion(profileName: String, version: String) {
        val profile = state.profiles.firstOrNull { it.name == profileName } ?: return
        if (profile.lastSeenVersion == version) return
        profile.lastSeenVersion = version
        // "auto" follows the router's version; the version banner depends on it too
        dev.dybug.routeros.schema.RosProjectSchemaService.getInstance(project).selectionChanged()
        com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications()
    }

    companion object {
        const val AUTO_SCHEMA = "auto"

        fun getInstance(project: Project): RosDeviceSettings = project.getService(RosDeviceSettings::class.java)
    }
}
