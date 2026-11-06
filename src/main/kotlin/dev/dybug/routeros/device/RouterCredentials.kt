package dev.dybug.routeros.device

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/** Router passwords in the IDE password safe, keyed by URL and user. Call off the EDT. */
object RouterCredentials {
    /** Identity of a stored password; the service name includes the user so accounts on one router do not collide. */
    data class Key(val url: String, val user: String)

    fun key(profile: RouterProfile) = Key(profile.url.trim().trimEnd('/'), profile.user.trim())

    private fun attributes(key: Key) = CredentialAttributes(generateServiceName("RouterOS Script", "${key.user}@${key.url}"), key.user)

    fun password(profile: RouterProfile): String = password(key(profile))

    fun password(key: Key): String = PasswordSafe.instance.get(attributes(key))?.getPasswordAsString().orEmpty()

    fun store(key: Key, password: String?) {
        PasswordSafe.instance.set(attributes(key), password?.takeIf { it.isNotEmpty() }?.let { Credentials(key.user, it) })
    }
}
