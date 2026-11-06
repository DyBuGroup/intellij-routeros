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
class RosDeviceSettings : PersistentStateComponent<RosDeviceSettings.State> {
    class State {
        @XCollection(style = XCollection.Style.v2)
        var profiles: MutableList<RouterProfile> = mutableListOf()
        var defaultProfile: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    val profiles: List<RouterProfile> get() = state.profiles

    fun profile(name: String?): RouterProfile? =
        state.profiles.firstOrNull { it.name == name } ?: state.profiles.firstOrNull { it.name == state.defaultProfile } ?: state.profiles.firstOrNull()

    fun update(profiles: List<RouterProfile>, defaultProfile: String) {
        state.profiles = profiles.map { it.copy() }.toMutableList()
        state.defaultProfile = defaultProfile
    }

    fun rememberVersion(profileName: String, version: String) {
        state.profiles.firstOrNull { it.name == profileName }?.lastSeenVersion = version
    }

    companion object {
        fun getInstance(project: Project): RosDeviceSettings = project.getService(RosDeviceSettings::class.java)
    }
}
