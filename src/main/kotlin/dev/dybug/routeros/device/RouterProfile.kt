package dev.dybug.routeros.device

import com.intellij.util.xmlb.annotations.Tag

/** A router connection. The password is not part of the profile; it lives in the IDE password safe. */
@Tag("profile")
class RouterProfile {
    var name: String = "default"
    var url: String = "https://192.168.88.1"
    var user: String = "admin"
    var tlsMode: TlsMode = TlsMode.VERIFY

    /** SHA-256 of the router certificate (hex, colon-free) when [tlsMode] is [TlsMode.PINNED]. */
    var pinnedSha256: String = ""
    var timeoutSeconds: Int = 600
    var proxyMode: ProxyMode = ProxyMode.AUTO

    /** Last RouterOS version seen by "Test connection" (drives the schema banner). */
    var lastSeenVersion: String = ""

    fun copy(): RouterProfile = RouterProfile().also {
        it.name = name
        it.url = url
        it.user = user
        it.tlsMode = tlsMode
        it.pinnedSha256 = pinnedSha256
        it.timeoutSeconds = timeoutSeconds
        it.proxyMode = proxyMode
        it.lastSeenVersion = lastSeenVersion
    }

    override fun toString(): String = name
}

enum class ProxyMode {
    /** Direct for local addresses (private, loopback, link-local, `.local`/`.lan`, single-label names), else the IDE proxy. */
    AUTO,

    /** Always the IDE's HTTP proxy settings. */
    IDE,

    /** Never a proxy. */
    NONE,
}

enum class TlsMode {
    /** Normal certificate and host name validation. */
    VERIFY,

    /** Accept exactly the certificate with the pinned SHA-256 fingerprint (typical for self-signed routers). */
    PINNED,

    /** Accept any certificate, and allow plain http:// URLs. Only for disposable lab routers. */
    INSECURE,
}
