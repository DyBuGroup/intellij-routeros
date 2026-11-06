package dev.dybug.routeros.device

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/** Router passwords in the IDE password safe, keyed by URL and user. Call off the EDT. */
object RouterCredentials {
    private fun attributes(profile: RouterProfile) =
        CredentialAttributes(generateServiceName("RouterOS Script", profile.url.trimEnd('/')), profile.user)

    fun password(profile: RouterProfile): String =
        PasswordSafe.instance.get(attributes(profile))?.getPasswordAsString().orEmpty()

    fun store(profile: RouterProfile, password: String?) {
        PasswordSafe.instance.set(attributes(profile), password?.let { Credentials(profile.user, it) })
    }
}
