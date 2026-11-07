package dev.dybug.routeros.device

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import dev.dybug.routeros.RosBundle
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/** Settings | Languages & Frameworks | RouterOS: connection profiles. Passwords go to the password safe. */
class RosDeviceConfigurable(private val project: Project) : Configurable {
    private val settings = RosDeviceSettings.getInstance(project)
    private val profiles = mutableListOf<RouterProfile>()
    private val passwords = mutableMapOf<RouterProfile, String>()
    private var defaultName = ""
    private var current: RouterProfile? = null

    private val profileModel = DefaultComboBoxModel<RouterProfile>()
    private val profileCombo = ComboBox(profileModel)
    private val nameField = JBTextField()
    private val urlField = JBTextField()
    private val userField = JBTextField()
    private val passwordField = JBPasswordField()
    private val tlsCombo = ComboBox(TlsMode.entries.toTypedArray())
    private val proxyCombo = ComboBox(ProxyMode.entries.toTypedArray()).apply {
        renderer = com.intellij.ui.SimpleListCellRenderer.create { label, value, _ ->
            label.text = RosBundle.message("device.settings.proxy." + (value ?: ProxyMode.AUTO).name.lowercase())
        }
    }
    private val pinField = JBTextField()
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(600, 5, 86_400, 5))
    private val defaultCheck = JBCheckBox(RosBundle.message("device.settings.default"))
    private val status = JBLabel()

    /** "auto" or a bundled version; shown with its channel and release. */
    private val schemaCombo = ComboBox(
        (listOf(RosDeviceSettings.AUTO_SCHEMA) + dev.dybug.routeros.schema.RosSchemaLoader.bundledVersions).toTypedArray(),
    ).apply {
        renderer = com.intellij.ui.SimpleListCellRenderer.create { label, value, _ -> label.text = schemaLabel(value) }
    }

    /** The stored choice, or "auto" when it names a version that is no longer bundled. */
    private fun normalizedSchemaVersion(): String =
        settings.state.schemaVersion.takeIf { it in dev.dybug.routeros.schema.RosSchemaLoader.bundledVersions } ?: RosDeviceSettings.AUTO_SCHEMA

    private fun schemaLabel(value: String?): String {
        if (value == null || value == RosDeviceSettings.AUTO_SCHEMA) return RosBundle.message("device.settings.schema.auto")
        val channels = dev.dybug.routeros.schema.RosSchemaLoader.channels.filter { it.version == value }
        return RosBundle.message("device.settings.schema.version", value, channels.joinToString(", ") { "${it.name} ${it.release}" })
    }

    override fun getDisplayName(): String = RosBundle.message("device.settings.title")

    override fun disposeUIResources() {
        uiDisposed = true
    }

    override fun createComponent(): JComponent {
        profileCombo.addActionListener {
            saveFields()
            current = profileCombo.selectedItem as? RouterProfile
            loadFields()
        }
        return panel {
            row(RosBundle.message("device.settings.profile")) {
                cell(profileCombo).align(AlignX.FILL).resizableColumn()
                cell(JButton(RosBundle.message("device.settings.add")).apply { addActionListener { addProfile() } })
                cell(JButton(RosBundle.message("device.settings.remove")).apply { addActionListener { removeProfile() } })
            }
            separator()
            row(RosBundle.message("device.settings.name")) { cell(nameField).align(AlignX.FILL) }
            row(RosBundle.message("device.settings.url")) { cell(urlField).align(AlignX.FILL).comment(RosBundle.message("device.settings.url.comment")) }
            row(RosBundle.message("device.settings.user")) { cell(userField).align(AlignX.FILL) }
            row(RosBundle.message("device.settings.password")) { cell(passwordField).align(AlignX.FILL) }
            row(RosBundle.message("device.settings.tls")) { cell(tlsCombo) }
            row(RosBundle.message("device.settings.proxy")) { cell(proxyCombo).comment(RosBundle.message("device.settings.proxy.comment")) }
            row(RosBundle.message("device.settings.pin")) {
                cell(pinField).align(AlignX.FILL).resizableColumn()
                cell(JButton(RosBundle.message("device.settings.pin.fetch")).apply { addActionListener { fetchPin() } })
            }
            row(RosBundle.message("device.settings.timeout")) { cell(timeoutSpinner) }
            row { cell(defaultCheck) }
            row {
                cell(JButton(RosBundle.message("device.settings.test")).apply { addActionListener { testConnection() } })
                cell(status)
            }
            row { comment(RosBundle.message("device.settings.policy.comment")) }
            separator()
            row(RosBundle.message("device.settings.schema")) {
                cell(schemaCombo).comment(RosBundle.message("device.settings.schema.comment"))
            }
        }
    }

    override fun reset() {
        profiles.clear()
        settings.profiles.forEach { profiles += it.copy() }
        defaultName = settings.state.defaultProfile
        schemaCombo.selectedItem = normalizedSchemaVersion()
        passwords.clear()
        passwordsLoaded = false
        passwordsChanged = false
        originalKeys.clear()
        current = null
        refreshCombo(profiles.firstOrNull { it.name == defaultName } ?: profiles.firstOrNull())
        // passwords are loaded off the EDT; the field stays read-only until then so no edit is lost
        passwordField.isEnabled = false
        val generation = ++loadGeneration
        val keys = profiles.associateWith { RouterCredentials.key(project, it) }
        originalKeys += keys.values
        CREDENTIAL_IO.execute {
            val loaded = keys.mapValues { (_, key) -> RouterCredentials.password(key) }
            ApplicationManager.getApplication().invokeLater({
                if (uiDisposed || generation != loadGeneration) return@invokeLater
                loaded.forEach { (profile, password) -> passwords.putIfAbsent(profile, password) }
                loaded.forEach { (profile, password) -> keys[profile]?.let { loadedPasswords[it] = password } }
                passwordsLoaded = true
                passwordField.isEnabled = current != null
                if (current != null) passwordField.text = passwords[current].orEmpty()
            }, ModalityState.any())
        }
    }

    override fun isModified(): Boolean {
        saveFields()
        val saved = settings.profiles
        if (saved.size != profiles.size || defaultName != settings.state.defaultProfile) return true
        if (schemaCombo.selectedItem != normalizedSchemaVersion()) return true
        if (saved.zip(profiles).any { (a, b) -> !same(a, b) }) return true
        return passwordsChanged
    }

    private var passwordsChanged = false
    private var passwordsLoaded = false
    private var loadGeneration = 0
    private var uiDisposed = false

    /** Credential keys as loaded, so that keys orphaned by URL/user edits can be removed on apply. */
    private val originalKeys = mutableSetOf<RouterCredentials.Key>()
    private val loadedPasswords = mutableMapOf<RouterCredentials.Key, String>()

    override fun apply() {
        saveFields()
        val names = profiles.map { it.name }
        if (names.any { it.isBlank() } || names.toSet().size != names.size) {
            throw com.intellij.openapi.options.ConfigurationException(RosBundle.message("device.settings.names.invalid"))
        }
        for (profile in profiles) {
            try {
                RouterRestClient.parseBase(profile.url)
            } catch (e: RouterException) {
                throw com.intellij.openapi.options.ConfigurationException("${profile.name}: ${e.message}")
            }
        }
        // snapshot immutable keys and values now; later UI edits must not change what gets stored
        val keysChanged = profiles.map { RouterCredentials.key(project, it) }.toSet() != originalKeys
        if (keysChanged && !passwordsLoaded) {
            throw com.intellij.openapi.options.ConfigurationException(RosBundle.message("device.settings.passwords.loading"))
        }
        // one password per credential key: profiles sharing URL and user must agree
        val byKey = profiles.groupBy { RouterCredentials.key(project, it) }
        val conflict = byKey.entries.firstOrNull { (_, group) -> group.map { passwords[it].orEmpty() }.distinct().size > 1 }
        if (conflict != null) {
            throw com.intellij.openapi.options.ConfigurationException(
                RosBundle.message("device.settings.password.conflict", conflict.key.user, conflict.key.url),
            )
        }
        settings.update(profiles, defaultName.takeIf { it in names } ?: names.firstOrNull().orEmpty())
        val schemaVersion = schemaCombo.selectedItem as? String ?: RosDeviceSettings.AUTO_SCHEMA
        if (schemaVersion != settings.state.schemaVersion) {
            settings.state.schemaVersion = schemaVersion
            dev.dybug.routeros.schema.RosProjectSchemaService.getInstance(project).selectionChanged()
        }
        com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications()
        if (passwordsLoaded && (passwordsChanged || keysChanged)) {
            // write only what changed: a password that failed to load (locked safe) must not be erased
            val toStore = byKey.mapValues { (_, group) -> passwords[group.first()].orEmpty() }
                .filter { (key, password) -> key !in originalKeys || loadedPasswords[key] != password }
            val orphaned = originalKeys - byKey.keys
            // one ordered queue for all credential reads and writes
            CREDENTIAL_IO.execute {
                orphaned.forEach { RouterCredentials.store(it, null) }
                toStore.forEach { (key, password) -> RouterCredentials.store(key, password) }
            }
            originalKeys.clear()
            originalKeys += byKey.keys
            loadedPasswords.clear()
            byKey.forEach { (key, group) -> loadedPasswords[key] = passwords[group.first()].orEmpty() }
        }
        passwordsChanged = false
    }

    private fun same(a: RouterProfile, b: RouterProfile) = a.name == b.name && a.url == b.url && a.user == b.user &&
        a.tlsMode == b.tlsMode && a.pinnedSha256 == b.pinnedSha256 && a.timeoutSeconds == b.timeoutSeconds &&
        a.proxyMode == b.proxyMode

    private fun addProfile() {
        saveFields()
        val profile = RouterProfile().apply { name = uniqueName() }
        passwords[profile] = ""
        profiles += profile
        if (profiles.size == 1) defaultName = profile.name
        refreshCombo(profile)
    }

    private fun removeProfile() {
        val profile = current ?: return
        profiles.remove(profile)
        passwords.remove(profile)
        current = null
        refreshCombo(profiles.firstOrNull())
    }

    private fun uniqueName(): String {
        var index = profiles.size + 1
        while (profiles.any { it.name == "router$index" }) index++
        return "router$index"
    }

    private fun refreshCombo(select: RouterProfile?) {
        current = null
        profileModel.removeAllElements()
        profiles.forEach(profileModel::addElement)
        profileCombo.selectedItem = select
        current = select
        loadFields()
    }

    private fun loadFields() {
        val profile = current
        val enabled = profile != null
        listOf(nameField, urlField, userField, tlsCombo, proxyCombo, pinField, timeoutSpinner, defaultCheck).forEach { it.isEnabled = enabled }
        passwordField.isEnabled = enabled && passwordsLoaded
        if (profile == null) return
        nameField.text = profile.name
        urlField.text = profile.url
        userField.text = profile.user
        passwordField.text = passwords[profile].orEmpty()
        tlsCombo.selectedItem = profile.tlsMode
        proxyCombo.selectedItem = profile.proxyMode
        pinField.text = profile.pinnedSha256
        timeoutSpinner.value = profile.timeoutSeconds
        defaultCheck.isSelected = profile.name == defaultName
        status.text = profile.lastSeenVersion.takeIf { it.isNotEmpty() }?.let { RosBundle.message("device.settings.last.version", it) }.orEmpty()
    }

    private fun saveFields() {
        val profile = current ?: return
        val oldName = profile.name
        profile.name = nameField.text.trim()
        profile.url = urlField.text.trim()
        profile.user = userField.text.trim()
        val password = String(passwordField.password)
        // until stored passwords are loaded, an empty field must not overwrite them
        if (passwordsLoaded && passwords[profile] != password) {
            // profiles with the same URL and user share one stored password
            val key = RouterCredentials.key(project, profile)
            profiles.filter { it === profile || RouterCredentials.key(project, it) == key }.forEach { passwords[it] = password }
            passwordsChanged = true
        }
        profile.tlsMode = tlsCombo.selectedItem as? TlsMode ?: TlsMode.VERIFY
        profile.proxyMode = proxyCombo.selectedItem as? ProxyMode ?: ProxyMode.AUTO
        profile.pinnedSha256 = pinField.text.trim().lowercase().replace(":", "")
        profile.timeoutSeconds = (timeoutSpinner.value as? Int) ?: 600
        if (defaultCheck.isSelected) {
            defaultName = profile.name
        } else if (defaultName == oldName) {
            defaultName = ""
        }
    }

    private fun testConnection() {
        saveFields()
        val profile = current?.copy() ?: return
        val password = passwords[current].orEmpty()
        status.text = RosBundle.message("device.settings.testing")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, RosBundle.message("device.settings.testing"), true) {
            override fun run(indicator: ProgressIndicator) {
                val message = try {
                    val resource = RouterConnections.client(profile, password).resource()
                    val version = resource.get("version")?.asString.orEmpty()
                    settings.rememberVersion(profile.name, version)
                    profiles.firstOrNull { it.name == profile.name }?.lastSeenVersion = version
                    RosBundle.message("device.settings.connected", version, resource.get("board-name")?.asString.orEmpty())
                } catch (e: RouterException) {
                    RosBundle.message("device.settings.failed", e.message.orEmpty())
                } catch (e: Exception) {
                    RosBundle.message("device.settings.failed", e.message ?: e.javaClass.simpleName)
                }
                ApplicationManager.getApplication().invokeLater({ status.text = message }, ModalityState.any())
            }
        })
    }

    private fun fetchPin() {
        val url = urlField.text.trim()
        ProgressManager.getInstance().run(object : Task.Modal(project, RosBundle.message("device.settings.pin.fetch"), true) {
            override fun run(indicator: ProgressIndicator) {
                val result = runCatching { RouterConnections.fetchFingerprint(url) }
                ApplicationManager.getApplication().invokeLater({
                    result.onSuccess { fingerprint ->
                        val answer = Messages.showYesNoDialog(
                            project,
                            RosBundle.message("device.settings.pin.confirm", fingerprint.chunked(2).joinToString(":")),
                            RosBundle.message("device.settings.pin.fetch"),
                            Messages.getQuestionIcon(),
                        )
                        if (answer == Messages.YES) {
                            pinField.text = fingerprint
                            tlsCombo.selectedItem = TlsMode.PINNED
                        }
                    }.onFailure {
                        Messages.showErrorDialog(project, it.message ?: it.javaClass.simpleName, RosBundle.message("device.settings.pin.fetch"))
                    }
                }, ModalityState.any())
            }
        })
    }

    private companion object {
        val CREDENTIAL_IO = com.intellij.util.concurrency.AppExecutorUtil.createBoundedApplicationPoolExecutor("RouterOS credentials", 1)
    }
}
