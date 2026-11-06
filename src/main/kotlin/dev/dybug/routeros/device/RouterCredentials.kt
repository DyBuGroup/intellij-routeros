package dev.dybug.routeros.device

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.project.Project

/**
 * Router passwords in the IDE password safe. Keys are per project, URL and user: a password stored
 * for one project is never used (or deleted) by another, so a cloned project with weaker TLS
 * settings cannot obtain it. Call off the EDT.
 */
object RouterCredentials {
    data class Key(val project: String, val url: String, val user: String)

    fun key(project: Project, profile: RouterProfile) = Key(project.locationHash, profile.url.trim().trimEnd('/'), profile.user.trim())

    private fun attributes(key: Key) =
        CredentialAttributes(generateServiceName("RouterOS Script", "${key.project}/${key.user}@${key.url}"), key.user)

    fun password(project: Project, profile: RouterProfile): String = password(key(project, profile))

    fun password(key: Key): String = PasswordSafe.instance.get(attributes(key))?.getPasswordAsString().orEmpty()

    fun store(key: Key, password: String?) {
        PasswordSafe.instance.set(attributes(key), password?.takeIf { it.isNotEmpty() }?.let { Credentials(key.user, it) })
    }
}
